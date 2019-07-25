package com.msilb.ordermatching

import com.msilb.ordermatching.State.Open

sealed trait State

object State {

  case object Open extends State

  case object Closed extends State

}

final case class OrderBook(instrumentName: String,
                           state: State = Open,
                           ordersById: Map[Long, Order] = Map.empty)
