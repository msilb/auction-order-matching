# Price-Time Priority Order Matching Engine for Auctions

This is an attempt to implement a simplified version of the order matching engines used in auctions (e.g. during opening or closing) on major exchanges.

Current limitations:
- Only Limit Orders are supported

## Auction Phases

### Phase 1: Book Building

In this first phase an order book is open and accepts orders. Orders may be also modified/amended and cancelled

### Phase 2: Price Determination Phase

Once Matching is initiated, order book is closed and no more new orders are accepted.

First of all, orders are grouped and sorted by
1. Side: Buy (Bid) or Sell (Ask)
2. Limit Price: Buy orders with higher limit price and Sell orders with lower limit price have higher priority for execution. In case of multiple orders having the same limit price, orders which arrived earlier (time priority) have higher priority.

Next, total volume for each price point is determined on both sides of the order book (Bid and Ask).

Total volumes are summed up from the top of the order book to build aggregate supply and demand for each price level.

Finally, from the minimum values of supply and demand aggregates, the maximum executable volume is determined across all price levels.

### Phase 3: Execution Phase

Provided the execution price and the execution volume determined in the previous phase, the orders can be executed. The rules specified above ensure that maximum of one order can be partially executed on both sides of the order book. All other orders would either be fully executed or remain open with full size.

## Example

Consider the following order book before the start of Phase 2:

Bid Quantity | Bid Price | Ask Price | Ask Quantity
------------ | --------- | --------- | ------------
100	| 104.5 | 100.5 |	600
2500 | 104.5 | 100.5 | 400
1800 | 103 | 102 | 1500
500 | 102.5 | 103 | 1200
800 | 102.5 | 104.5 | 700
1500 | 99.5 | | 
