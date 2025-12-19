package zionomicon.exercises

package StmDataStructures {

  /**
   *   1. Develop a real-time trading system that uses STM for placing orders
   *      and matching them on the order book. The system should support the
   *      following operations:
   *      - Place a new order
   *      - Cancel an order
   *      - Update an order
   *      - Match orders
   *
   * Hint: Use `TPriorityQueue` to store the sell and buy orders and a `TMap` to
   * store all the order books for each stock:
   *
   * {{{
   * type Stock = String
   *
   * case class Order(
   *   id: Long,
   *   stock: Stock,
   *   price: Double,
   *   quantity: Int,
   *   isBuy: Boolean
   * )
   *
   * case class OrderBook(
   *   buyQueue: TPriorityQueue[Order],
   *   sellQueue: TPriorityQueue[Order]
   * )
   *
   * case class TradingSystem(orderBooks: TMap[Stock, OrderBook])
   * }}}
   *
   * Please note that this is for pedagogical purposes only and is not intended
   * for a real-world trading system. Maintaining the order book in real-world
   * trading systems is much more complex and typically involves using more
   * advanced architectural patterns and data structures.
   */
  package TradingSystemApp {}

}
