package com.msilb.ordermatching

import com.msilb.ordermatching.ExecutionStatus.Pending

sealed trait Side

object Side {

  object Buy extends Side

  object Sell extends Side

}

sealed trait ExecutionStatus

object ExecutionStatus {

  object Pending extends ExecutionStatus

  object PartiallyExecuted extends ExecutionStatus

  object FullyExecuted extends ExecutionStatus

}

final case class Order(id: Long,
                       entryTimestamp: Long,
                       side: Side,
                       quantity: Int,
                       price: Double,
                       var execution: Option[Execution] = None,
                       var executionStatus: ExecutionStatus = Pending)

final case class Execution(executionPrice: Double, executionQuantity: Int)
