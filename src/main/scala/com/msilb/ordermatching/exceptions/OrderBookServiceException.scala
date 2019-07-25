package com.msilb.ordermatching.exceptions

final case class OrderBookServiceException(private val message: String = "",
                                           private val cause: Throwable = None.orNull) extends Exception(message, cause)
