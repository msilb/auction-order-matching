[![Build Status](https://travis-ci.org/msilb/auction-order-matching.svg?branch=master)](https://travis-ci.org/msilb/auction-order-matching)

# Price-Time Priority Order Matching Engine for Auctions

This is an attempt to implement a simplified version of the order matching engines used in auctions (e.g. during opening or closing) on major exchanges.

Current limitations:
- Only Limit Orders are supported

## Auction Phases

### Phase 1: Book Building

In this first phase an order book is open and accepts new orders. Orders may also be modified/amended or cancelled.

### Phase 2: Price Determination Phase

Once Matching is initiated, order book is closed and no more orders are accepted.

First of all, orders are grouped and sorted by
1. Side: Buy (Bid) or Sell (Ask)
2. Limit Price: Buy orders with higher limit price and Sell orders with lower limit price have higher priority for execution. In case of multiple orders having the same limit price, orders which arrived earlier (time priority) have higher priority.

Next, total volume for each price point is determined on both sides of the order book (Bid and Ask).

Total volumes are summed up from the top of the order book to build aggregate supply and demand for each price level.

Finally, from the minimum values of supply and demand aggregates, the maximum executable volume is determined across all price levels.

### Phase 3: Execution Phase

Provided the execution price and the execution volume determined in the previous phase, the orders can be executed. The rules specified above ensure that maximum of one order can be partially executed on either side of the order book. All other orders would either be fully executed or would remain open with full size.

## Example

Consider the following order book before the start of Phase 2:

Order | Bid Quantity | Bid Price | Ask Price | Ask Quantity | Order
----- | ------------ | --------- | --------- | ------------ | -----
B1    | 100          | 104.5     | 100.5     | 600          | S1
B2    | 2500         | 104.5     | 100.5     | 400          | S2
B3    | 1800         | 103       | 102       | 1500         | S3
B4    | 500          | 102.5     | 103       | 1200         | S4
B5    | 800          | 102.5     | 104.5     | 700          | S5
B6    | 1500         | 99.5      | --        | --           | --

Combining price levels from bid and ask, grouping orders and calculating total and aggregate supply & demand yields the following result:

Buy Orders | Aggregate Demand | Total Demand | Price   | Total Supply | Aggregate Supply | Sell Orders
---------- | ---------------- | ------------ | -----   | ------------ | ---------------- | -----------
B1, B2     | 2600             | 2600         | 104.5   | 700          | 4400             | S5
**B3**     | **4400**         | **1800**     | **103** | **1200**     | **3700**         | **S4**
B4, B5     | 5700             | 1300         | 102.5   | 0            | 2500             | --
--         | 5700             | 0            | 102     | 1500         | 2500             | S3
--         | 5700             | 0            | 100.5   | 1000         | 1000             | S1, S2
B6         | 7200             | 1500         | 99.5    | 0            | 0                | --

Aggregation is done from top to bottom on the bid side and from bottom to top on the ask side (i.e. always from the _best_ price on either side). To find the maximum executable volume, one needs to look at the aggregate supply and demand values across all price levels and find the max. For this purpose we need to consider the min of both aggregate values (supply and demand) since only so much can be satisfied for any given price level. In this case our auction price is determined as **103** and our max execution volume is **3700** (=min of 3700 and 4400).

Once these two values have been determined, we can go back to Table 1 and proceed with order execution:

**Execution Price: 103**

Order | **Execution** | Bid Quantity | Bid Price | Ask Price | Ask Quantity | **Execution** | Order
----- | ------------- | ------------ | --------- | --------- | ------------ | ------------- | -----
B1    | **100**       | 100          | 104.5     | 100.5     | 600          | **600**       | S1
B2    | **2500**      | 2500         | 104.5     | 100.5     | 400          | **400**       | S2
B3    | **1100**      | 1800         | 103       | 102       | 1500         | **1500**      | S3
B4    | **0**         | 500          | 102.5     | 103       | 1200         | **1200**      | S4
B5    | **0**         | 800          | 102.5     | 104.5     | 700          | **0**         | S5
B6    | **0**         | 1500         | 99.5      | --        | --           | --            | --
