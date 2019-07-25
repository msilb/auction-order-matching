package com.msilb.ordermatching

import com.msilb.ordermatching.ExecutionStatus.{FullyExecuted, PartiallyExecuted, Pending}
import com.msilb.ordermatching.Side.{Buy, Sell}
import com.msilb.ordermatching.exceptions.OrderBookServiceException
import org.scalatest._

class OrderBookServiceSpec extends FlatSpec with Matchers {

  "An OrderBookService" should "throw error if order book already exists" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")
    assertThrows[OrderBookServiceException] {
      orderBookService.createOrderBook("IBM")
    }
  }

  it should "throw error if instrument cannot be found" in {

    val orderBookService = new OrderBookServiceImpl()
    assertThrows[OrderBookServiceException] {
      orderBookService.newOrder("BLA", Buy, 10000, 1.2)
    }
  }

  it should "throw error when creating new order if order book is in Closed state" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")
    orderBookService.closeOrderBook("IBM", orderBookService.findOrderBook("IBM"))
    assertThrows[OrderBookServiceException] {
      orderBookService.newOrder("IBM", Buy, 10000, 1.2)
    }
  }

  it should "throw error when amending an order if order book is in Closed state" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")
    orderBookService.newOrder("IBM", Buy, 10000, 1.2)
    orderBookService.closeOrderBook("IBM", orderBookService.findOrderBook("IBM"))
    assertThrows[OrderBookServiceException] {
      orderBookService.amendOrder("IBM", 1, Buy, 10000, 1.2)
    }
  }

  it should "throw error when cancelling an order if order book is in Closed state" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")
    orderBookService.newOrder("IBM", Buy, 10000, 1.2)
    orderBookService.closeOrderBook("IBM", orderBookService.findOrderBook("IBM"))
    assertThrows[OrderBookServiceException] {
      orderBookService.cancelOrder("IBM", 1)
    }
  }

  it should "successfully create, amend and cancel orders" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")

    val orderId1 = delayed(orderBookService.newOrder("IBM", Sell, 15000, 110.5))
    delayed(orderBookService.amendOrder("IBM", orderId1, Buy, 3000, 110.5))

    val orderId2 = delayed(orderBookService.newOrder("IBM", Sell, 1000, 115.5))
    delayed(orderBookService.cancelOrder("IBM", orderId2))

    val orderBook = orderBookService.findOrderBook("IBM")
    assert(orderBook.instrumentName == "IBM")

    assert(orderBook.ordersById(orderId1).quantity == 3000)
    assert(orderBook.ordersById(orderId1).price == 110.5)

    assert(orderBook.ordersById.get(orderId2).isEmpty)
  }

  it should "successfully match and execute orders 1" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("IBM")

    val orderId1 = delayed(orderBookService.newOrder("IBM", Buy, 10000, 105.5))
    val orderId2 = delayed(orderBookService.newOrder("IBM", Buy, 200, 104))
    val orderId3 = delayed(orderBookService.newOrder("IBM", Buy, 5600, 104.5))
    val orderId4 = delayed(orderBookService.newOrder("IBM", Sell, 1000, 104.5))
    val orderId5 = delayed(orderBookService.newOrder("IBM", Sell, 6900, 103))
    val orderId6 = delayed(orderBookService.newOrder("IBM", Sell, 300, 106))
    val orderId7 = delayed(orderBookService.newOrder("IBM", Sell, 2500, 100.5))

    orderBookService.matchOrders("IBM")

    val orderBook = orderBookService.findOrderBook("IBM")

    def assertOrderExecution(order: Order,
                             expectedExecutionStatus: ExecutionStatus,
                             expectedExecution: Option[Execution]): Unit = {
      assert(order.executionStatus == expectedExecutionStatus)
      assert(order.execution == expectedExecution)
    }

    assertOrderExecution(orderBook.ordersById(orderId1), FullyExecuted, Some(Execution(104.5, 10000)))
    assertOrderExecution(orderBook.ordersById(orderId3), PartiallyExecuted, Some(Execution(104.5, 400)))
    assertOrderExecution(orderBook.ordersById(orderId2), Pending, None)

    assertOrderExecution(orderBook.ordersById(orderId7), FullyExecuted, Some(Execution(104.5, 2500)))
    assertOrderExecution(orderBook.ordersById(orderId5), FullyExecuted, Some(Execution(104.5, 6900)))
    assertOrderExecution(orderBook.ordersById(orderId4), FullyExecuted, Some(Execution(104.5, 1000)))
    assertOrderExecution(orderBook.ordersById(orderId6), Pending, None)
  }

  it should "successfully match and execute orders 2" in {

    val orderBookService = new OrderBookServiceImpl()
    orderBookService.createOrderBook("GOOG")

    val orderId1 = delayed(orderBookService.newOrder("GOOG", Buy, 2500, 104.5))
    val orderId2 = delayed(orderBookService.newOrder("GOOG", Buy, 1800, 103))
    val orderId3 = delayed(orderBookService.newOrder("GOOG", Buy, 100, 104.5))
    val orderId4 = delayed(orderBookService.newOrder("GOOG", Buy, 1500, 99.5))
    val orderId5 = delayed(orderBookService.newOrder("GOOG", Buy, 800, 102.5))
    val orderId6 = delayed(orderBookService.newOrder("GOOG", Buy, 500, 102.5))
    val orderId7 = delayed(orderBookService.newOrder("GOOG", Sell, 1200, 103))
    val orderId8 = delayed(orderBookService.newOrder("GOOG", Sell, 600, 100.5))
    val orderId9 = delayed(orderBookService.newOrder("GOOG", Sell, 700, 104.5))
    val orderId10 = delayed(orderBookService.newOrder("GOOG", Sell, 400, 100.5))
    val orderId11 = delayed(orderBookService.newOrder("GOOG", Sell, 1500, 102))

    orderBookService.matchOrders("GOOG")

    val orderBook = orderBookService.findOrderBook("GOOG")

    def assertOrderExecution(order: Order,
                             expectedExecutionStatus: ExecutionStatus,
                             expectedExecution: Option[Execution]): Unit = {
      assert(order.executionStatus == expectedExecutionStatus)
      assert(order.execution == expectedExecution)
    }

    assertOrderExecution(orderBook.ordersById(orderId3), FullyExecuted, Some(Execution(103, 100)))
    assertOrderExecution(orderBook.ordersById(orderId1), FullyExecuted, Some(Execution(103, 2500)))
    assertOrderExecution(orderBook.ordersById(orderId2), PartiallyExecuted, Some(Execution(103, 1100)))
    assertOrderExecution(orderBook.ordersById(orderId6), Pending, None)
    assertOrderExecution(orderBook.ordersById(orderId5), Pending, None)
    assertOrderExecution(orderBook.ordersById(orderId4), Pending, None)

    assertOrderExecution(orderBook.ordersById(orderId8), FullyExecuted, Some(Execution(103, 600)))
    assertOrderExecution(orderBook.ordersById(orderId10), FullyExecuted, Some(Execution(103, 400)))
    assertOrderExecution(orderBook.ordersById(orderId11), FullyExecuted, Some(Execution(103, 1500)))
    assertOrderExecution(orderBook.ordersById(orderId7), FullyExecuted, Some(Execution(103, 1200)))
    assertOrderExecution(orderBook.ordersById(orderId9), Pending, None)
  }

  private def delayed[A](a: => A): A = {
    Thread.sleep(1L)
    val x = a
    x
  }
}
