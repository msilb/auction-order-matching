package com.msilb.ordermatching

import java.util.concurrent.atomic.AtomicInteger

import com.msilb.ordermatching.ExecutionStatus.{FullyExecuted, PartiallyExecuted}
import com.msilb.ordermatching.Side.Buy
import com.msilb.ordermatching.State.{Closed, Open}
import com.msilb.ordermatching.exceptions.OrderBookServiceException

import scala.collection.immutable.SortedMap

sealed trait OrderBookService {

  /**
   * Create new order book with a given instrument name
   *
   * @param instrument name of the instrument
   */
  def createOrderBook(instrument: String): Unit

  /**
   * Find order book for a given instrument name
   *
   * @param instrument name of the instrument
   * @return order book for the given instrument
   */
  def findOrderBook(instrument: String): OrderBook

  /**
   * Create new order in an order book for a given instrument and with specified input parameters
   *
   * @param instrument name of the instrument
   * @param side       order side [[com.msilb.ordermatching.Side.Buy]] or [[com.msilb.ordermatching.Side.Sell]]
   * @param quantity   order quantity
   * @param price      order limit price
   * @return id of the newly created order
   */
  def newOrder(instrument: String, side: Side, quantity: Int, price: Double): Long

  /**
   * Amend order in an order book for a given instrument and order id and with specified input parameters
   *
   * @param instrument name of the instrument
   * @param orderId    order id
   * @param side       order side [[com.msilb.ordermatching.Side.Buy]] or [[com.msilb.ordermatching.Side.Sell]]
   * @param quantity   order quantity
   * @param price      order limit price
   */
  def amendOrder(instrument: String, orderId: Long, side: Side, quantity: Int, price: Double): Unit

  /**
   * Cancel order in an order book for a given instrument and order id
   *
   * @param instrument name of the instrument
   * @param orderId    order id
   */
  def cancelOrder(instrument: String, orderId: Long): Unit

  /**
   * Perform auction-style order matching routine for a given instrument yielding executions
   *
   * @param instrument name of the instrument
   */
  def matchOrders(instrument: String): Unit
}

class OrderBookServiceImpl extends OrderBookService {

  private type OrderBookSide = SortedMap[Double, Seq[Order]]
  private val orderCounter = new AtomicInteger(1)
  private var orderBooks = Map.empty[String, OrderBook]

  override def createOrderBook(instrument: String): Unit = {
    if (orderBooks.contains(instrument))
      throw OrderBookServiceException(s"order book $instrument already exists!")
    orderBooks = orderBooks.updated(instrument, OrderBook(instrument))
  }

  override def findOrderBook(instrument: String): OrderBook = {
    orderBooks.getOrElse(instrument, throw OrderBookServiceException(s"order book $instrument does not exist!"))
  }

  override def newOrder(instrument: String, side: Side, quantity: Int, price: Double): Long = {
    withOrderBook(instrument, orderBook => {
      val orderId = orderCounter.getAndIncrement()
      insertOrUpdateOrder(orderBook, orderId, side, quantity, price)
      orderId
    })
  }

  override def amendOrder(instrument: String, orderId: Long, side: Side, quantity: Int, price: Double): Unit = {
    withOrderBook(instrument, orderBook => {
      insertOrUpdateOrder(orderBook, orderId, side, quantity, price)
    })
  }

  override def cancelOrder(instrument: String, orderId: Long): Unit = {
    withOrderBook(instrument, orderBook => {
      val updatedOrdersById = orderBook.ordersById.removed(orderId)
      orderBooks = orderBooks.updated(instrument, orderBook.copy(ordersById = updatedOrdersById))
    })
  }

  override def matchOrders(instrument: String): Unit = {
    val orderBook = findOrderBook(instrument)

    closeOrderBook(instrument, orderBook)

    val (bids, asks) = transformOrderBook(orderBook.ordersById.values.toSeq)

    val (auctionPrice, auctionVolume) = determineAuctionPriceAndVolume(bids, asks)

    executeOrders(bids, auctionPrice, auctionVolume)
    executeOrders(asks, auctionPrice, auctionVolume)
  }

  private def insertOrUpdateOrder(orderBook: OrderBook, orderId: Long, side: Side, quantity: Int, price: Double): Unit = {
    val order = Order(orderId, System.currentTimeMillis(), side, quantity, price)
    val updatedOrders = orderBook.ordersById.updated(orderId, order)
    orderBooks = orderBooks.updated(orderBook.instrumentName, orderBook.copy(ordersById = updatedOrders))
  }

  private def withOrderBook[T](instrument: String, f: OrderBook => T): T = {
    val orderBook = findOrderBook(instrument)
    if (orderBook.state == Open) {
      f(orderBook)
    } else {
      throw OrderBookServiceException(s"order book ${orderBook.instrumentName} is closed!")
    }
  }

  private def transformOrderBook(orders: Seq[Order]): (OrderBookSide, OrderBookSide) = {
    val (bids, asks) = orders.partition(_.side == Buy)
    val bidsByPrice = sortByPrice(bids.sortBy(_.entryTimestamp).groupBy(_.price))(Ordering.Double.TotalOrdering.reverse)
    val asksByPrice = sortByPrice(asks.sortBy(_.entryTimestamp).groupBy(_.price))(Ordering.Double.TotalOrdering)
    (bidsByPrice, asksByPrice)
  }

  private def sortByPrice[T](map: Map[Double, T])(implicit ordering: Ordering[Double]): SortedMap[Double, T] = {
    SortedMap.from(map)(ordering)
  }

  private def executeOrders(ordersByPrice: OrderBookSide, auctionPrice: Double, auctionVolume: Int): Unit = {
    var remainingBidExecQuantity = auctionVolume

    for {
      orders <- ordersByPrice.valuesIterator
      order <- orders.sortBy(_.entryTimestamp).iterator
      if remainingBidExecQuantity > 0
    } {
      val executionQuantity = if (order.quantity >= remainingBidExecQuantity) remainingBidExecQuantity else order.quantity
      remainingBidExecQuantity -= executionQuantity
      order.execution = Some(Execution(auctionPrice, executionQuantity))
      order.executionStatus = if (executionQuantity == order.quantity) FullyExecuted else PartiallyExecuted
    }
  }

  private def determineAuctionPriceAndVolume(bids: OrderBookSide, asks: OrderBookSide): (Double, Int) = {
    // All prices from both bid and ask sides
    val allPrices = (bids.keySet union asks.keySet).unsorted

    def calcSum(ordersByPrice: OrderBookSide) = {
      allPrices
        .map(p => p -> ordersByPrice.getOrElse(p, Seq.empty).map(_.quantity).sum)
        .toMap
    }

    // Calculate sum quantity of all orders for each price level + sort by price for bid and ask sides
    val bidTotals = sortByPrice(calcSum(bids))(Ordering.Double.TotalOrdering.reverse)
    val askTotals = sortByPrice(calcSum(asks))(Ordering.Double.TotalOrdering)

    // Calculate running totals (aggregates) of sum order volumes
    val bidAggregates = calcAggregate(bidTotals)
    val askAggregates = calcAggregate(askTotals)

    // Find min of aggregates between demand (bid) and supply (ask) sides across all price points
    val minAggregates = (bidAggregates.toSeq ++ askAggregates.toSeq)
      .groupBy { case (price, _) => price }
      .view
      .mapValues(el => el.map { case (_, sum) => sum }.toList.min)

    // Finally, determine the price(s) for which executable volume is maximized
    val maxPriceAndVolumes = minAggregates.filter { case (_, v) => v == minAggregates.values.max }

    // In case of multiple price levels with the same max executable volume we need to build the average
    (maxPriceAndVolumes.keysIterator.next(), maxPriceAndVolumes.values.sum / maxPriceAndVolumes.values.size)
  }

  private def calcAggregate(totalsByPrice: SortedMap[Double, Int]): Map[Double, Int] = {
    val aggregates = totalsByPrice
      .values
      .scanLeft(0)((acc, value) => acc + value)
      .drop(1)

    totalsByPrice.keysIterator.zip(aggregates).toMap
  }

  def closeOrderBook(instrument: String, orderBook: OrderBook): Unit = {
    orderBooks = orderBooks.updated(instrument, orderBook.copy(state = Closed))
  }
}
