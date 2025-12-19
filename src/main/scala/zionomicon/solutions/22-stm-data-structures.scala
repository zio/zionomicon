package zionomicon.solutions

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
  package TradingSystemApp {
    import zio._
    import zio.stm._

    import scala.io.StdIn

    /**
     * Real-time trading system with separated order placement and matcher
     * lifecycles
     *
     * Notes:
     *   - Order placement is a simple, fast operation that just adds orders to
     *     order books
     *   - Matcher lifecycle is completely independent - managed by a separate
     *     supervisor fiber
     *   - Supervisor watches reactively for new order books and spawns matchers
     *     on-demand
     *   - Console reader and matcher supervisor are spawned together in the
     *     main scope
     *   - When console reader exits and scope closes, all matcher fibers are
     *     interrupted
     *
     * Operations:
     *   - Order placement: adds orders to order books atomically
     *   - Order cancellation: removes orders from the system
     *   - Order updates: modifies price and/or quantity
     */
    object types {
      type Stock     = String
      type Timestamp = Long
      def printLine(line: String): UIO[Unit] =
        Console.printLine(line).orDie
    }
    import types._

    final case class Order(
      id: Long,
      stock: Stock,
      price: Double,
      quantity: Int,
      isBuy: Boolean,
      timestamp: Timestamp
    )

    final case class Trade(
      buyOrderId: Long,
      sellOrderId: Long,
      stock: Stock,
      price: Double,
      quantity: Int
    )

    final case class OrderBook(
      buyQueue: TPriorityQueue[Order],
      sellQueue: TPriorityQueue[Order]
    ) {

      /**
       * Attempt to match a single buy order with a single sell order
       */
      def attemptOneMatch: USTM[Trade] =
        for {
          buyOrder  <- buyQueue.peek
          sellOrder <- sellQueue.peek

          matchResult <-
            if (buyOrder.price >= sellOrder.price) {
              val matchedQty =
                Math.min(buyOrder.quantity, sellOrder.quantity)
              val trade = Trade(
                buyOrderId = buyOrder.id,
                sellOrderId = sellOrder.id,
                stock = sellOrder.stock,
                price = sellOrder.price,
                quantity = matchedQty
              )

              val newBuyQty  = buyOrder.quantity - matchedQty
              val newSellQty = sellOrder.quantity - matchedQty

              for {
                _ <- buyQueue.take
                _ <- sellQueue.take

                _ <- STM.when(newBuyQty > 0) {
                       buyQueue.offer(
                         buyOrder.copy(quantity = newBuyQty)
                       )
                     }
                _ <- STM.when(newSellQty > 0) {
                       sellQueue.offer(
                         sellOrder.copy(quantity = newSellQty)
                       )
                     }
              } yield trade
            } else STM.retry
        } yield matchResult

      def find(orderId: Long): USTM[Option[Order]] =
        buyQueue.toList.flatMap { buyOrders =>
          buyOrders.find(_.id == orderId) match {
            case Some(order) => STM.succeed(Some(order))
            case None =>
              sellQueue.toList.map { sellOrders =>
                sellOrders.find(_.id == orderId)
              }
          }
        }

      def cancel(orderId: Long): USTM[Boolean] = for {
        buyOrders  <- buyQueue.toList
        sellOrders <- sellQueue.toList
        found = buyOrders.exists(_.id == orderId) ||
                  sellOrders.exists(_.id == orderId)
        _ <- buyQueue.removeIf(_.id == orderId)
        _ <- sellQueue.removeIf(_.id == orderId)
      } yield found

      def place(order: Order): USTM[Long] =
        for {
          _ <- if (order.isBuy)
                 buyQueue.offer(order)
               else
                 sellQueue.offer(order)
        } yield order.id

      def update(
        orderId: Long,
        newPrice: Option[Double],
        newQuantity: Option[Int]
      ): USTM[Boolean] =
        find(orderId).flatMap {
          case None => STM.succeed(false)
          case Some(order) =>
            val updatedPrice = newPrice.getOrElse(order.price)
            val updatedQty   = newQuantity.getOrElse(order.quantity)

            if (updatedQty <= 0)
              cancel(orderId)
            else {
              val queue = if (order.isBuy) buyQueue else sellQueue
              val updatedOrder =
                order.copy(price = updatedPrice, quantity = updatedQty)

              queue.removeIf(_.id == orderId) *>
                queue.offer(updatedOrder).as(true)
            }
        }
    }

    object OrderBook {
      def empty: USTM[OrderBook] =
        for {
          buyQ <-
            TPriorityQueue.empty[Order](buyOrderOrdering)
          sellQ <-
            TPriorityQueue.empty[Order](sellOrderOrdering)
        } yield OrderBook(buyQ, sellQ)

      // Buy orders: highest price first, then earliest timestamp
      implicit def buyOrderOrdering: Ordering[Order] =
        Ordering.by[Order, (Double, Timestamp)] { order =>
          (-order.price, order.timestamp)
        }

      // Sell orders: lowest price first, then earliest timestamp
      implicit def sellOrderOrdering: Ordering[Order] =
        Ordering.by[Order, (Double, Timestamp)] { order =>
          (order.price, order.timestamp)
        }

    }

    final case class TradingSystem(
      private val orderBooks: TMap[Stock, OrderBook],
      private val trades: TRef[List[Trade]],
      private val handledStocks: TSet[Stock],
      private val matchers: Ref[Map[Stock, Fiber[Nothing, Unit]]],
      private val nextOrderId: Ref[Long]
    ) {

      def orderBook(stock: Stock): USTM[OrderBook] =
        for {
          maybeBook <- orderBooks.get(stock)
          book <- maybeBook match {
                    case Some(b) => STM.succeed(b)
                    case None =>
                      for {
                        newBook <- OrderBook.empty
                        _       <- orderBooks.put(stock, newBook)
                      } yield newBook
                  }
        } yield book

      private def recordTrade(trade: Trade): USTM[Unit] =
        trades.update(_ :+ trade)

      /**
       * Long-running matcher process for a specific stock
       */
      private def startMatcherForStock(stock: Stock): UIO[Nothing] =
        STM.atomically {
          for {
            book  <- orderBooks.getOrElseSTM(stock, ZSTM.retry)
            trade <- book.attemptOneMatch
            _     <- recordTrade(trade)
          } yield ()
        }.forever

      /**
       * Spawn a new matcher fiber for a stock Returns a fiber that will run in
       * the current scope
       */
      private def spawnMatcher(
        stock: Stock
      ): ZIO[Scope, Nothing, Fiber[Nothing, Unit]] =
        startMatcherForStock(stock).forkScoped

      /**
       * Matcher supervisor: independently manages matcher lifecycle using STM
       * coordination
       *
       * Instead of a busy loop that continuously polls, this uses STM.retry to
       * wait for new stocks:
       *   1. Gets all current stocks from order books
       *   2. Finds stocks that haven't been handled yet
       *   3. If no new stocks found, uses STM.retry to block and wait
       *   4. When new stock is detected, atomically marks it as handled
       *   5. Spawns the matcher fiber for that stock
       *   6. Loops back to find the next new stock
       *
       * This is fully reactive - no busy-waiting, no polling intervals.
       * STM.retry automatically coordinates between order placement and matcher
       * spawning.
       */
      def startMatcherSupervisor: ZIO[Scope, Nothing, Nothing] = {
        for {
          // Get the next unhandled stocks
          nextStocks <- STM.atomically {
                          for {
                            currentStocks <- orderBooks.keys.map(_.toSet)
                            handled       <- handledStocks.toSet
                            newStocks      = currentStocks -- handled

                            // If no new stocks, retry waits until a new order book is created
                            nextStocks <- if (newStocks.isEmpty) STM.retry
                                          else STM.succeed(newStocks)
                          } yield nextStocks
                        }

          _ <- ZIO.acquireRelease {
                 // Spawn matchers for all new stocks concurrently
                 ZIO.foreachParDiscard(nextStocks) { stock =>
                   spawnMatcher(stock).flatMap(fiber =>
                     matchers.update(_ + (stock -> fiber))
                   )
                 }
               } { _ =>
                 // mark stocks as handled
                 STM.atomically {
                   STM.foreachDiscard(nextStocks)(handledStocks.put)
                 }
               }
        } yield ()
      }.forever

      /**
       * Find which stock an order belongs to by searching all order books Used
       * internally for cancel and update operations to locate an order
       */
      private def findStockForOrder(orderId: Long): USTM[Option[Stock]] =
        orderBooks.findSTM { case (stock, orderBook) =>
          orderBook.find(orderId).map(_.map(_ => stock))
        }.map(_.flatten)

      def cancelOrder(orderId: Long): USTM[Boolean] =
        findStockForOrder(orderId).flatMap {
          case None =>
            STM.succeed(false)
          case Some(stock) =>
            for {
              book   <- orderBooks.getOrElseSTM(stock, ZSTM.retry)
              result <- book.cancel(orderId)
            } yield result
        }

      /**
       * Update an order's price and/or quantity
       *
       * Modifies the order by removing it from the queue and re-inserting with
       * updated values. If the new quantity is <= 0, the order is cancelled
       * instead.
       *
       * Returns true if order was successfully updated, false if order not
       * found
       */
      def updateOrder(
        orderId: Long,
        newPrice: Option[Double] = None,
        newQuantity: Option[Int] = None
      ): USTM[Boolean] =
        findStockForOrder(orderId).flatMap {
          case None =>
            STM.succeed(false)
          case Some(stock) =>
            orderBooks.getOrElseSTM(stock, ZSTM.retry).flatMap {
              _.update(orderId, newPrice, newQuantity)
            }
        }

      /**
       * Place a new order in the system (fast, non-blocking)
       *
       * Pure order placement operation:
       *   1. Creates or retrieves the order book for the stock
       *   2. Adds the order to the appropriate queue (buy or sell)
       *
       * The buy and sell queues are the single source of truth for all active
       * orders with their current quantities.
       *
       * This is a simple, atomic transactional operation.
       */
      def placeOrder(order: Order): UIO[Long] =
        orderBook(order.stock).flatMap(_.place(order).as(order.id)).commit

      /**
       * Get all executed trades
       */
      def getTrades: UIO[List[Trade]] =
        trades.get.commit

      /**
       * Get current order book snapshot for a stock
       *
       * Reads directly from the buy and sell queues, which are the source of
       * truth for all active orders.
       */
      def orderBookSnapshot(
        stock: Stock
      ): UIO[Option[(List[Order], List[Order])]] =
        STM.atomically {
          for {
            maybeBook <- orderBooks.get(stock)
            result <- maybeBook match {
                        case None => STM.succeed(None)
                        case Some(book) =>
                          for {
                            buyOrders  <- book.buyQueue.toList
                            sellOrders <- book.sellQueue.toList
                          } yield Some((buyOrders, sellOrders))
                      }
          } yield result
        }

      /**
       * Interactive console reader for placing, updating, and cancelling orders
       *
       * Returns a boolean to control looping:
       *   - true: continue looping (read next command)
       *   - false: exit loop gracefully
       *
       * This runs in the main fiber and blocks on user input:
       *   - Calls placeOrder, cancelOrder, updateOrder which are fast
       *     transactional operations
       *   - Each command returns true to continue or false to exit
       *   - When "exit" is typed, returns false
       *   - Loop stops, console reader returns, and scope closes
       *   - All matcher fibers are automatically cleaned up
       *   - No exceptions thrown on normal shutdown
       */
      def startConsoleReader: ZIO[Scope, Nothing, Unit] = {
        def loop: ZIO[Scope, Nothing, Unit] =
          for {
            _ <- printLine("\n[Enter order or 'help' for commands]> ")
            input <-
              ZIO
                .attempt(StdIn.readLine())
                .orElse(ZIO.succeed(""))

            shouldContinue <-
              input.trim.toLowerCase match {
                case "exit" => printLine("Shutting down...").as(false)

                case "help" =>
                  showHelp().as(true)

                case "trades" =>
                  for {
                    allTrades <- getTrades
                    _ <-
                      if (allTrades.isEmpty) printLine("No trades executed yet")
                      else
                        printLine("=== Executed Trades ===") *>
                          ZIO.foreach(allTrades) { trade =>
                            printLine(
                              s"Buy Order ${trade.buyOrderId} <-> Sell Order ${trade.sellOrderId}: " +
                                s"${trade.quantity} ${trade.stock} @ ${trade.price}"
                            )
                          }
                  } yield true

                case cmd if cmd.startsWith("book ") =>
                  val stock =
                    input.trim.substring(5).toUpperCase
                  for {
                    snapshot <- orderBookSnapshot(stock)
                    _ <-
                      snapshot match {
                        case None => printLine(s"No order book for $stock")
                        case Some((buyOrders, sellOrders)) =>
                          for {
                            _ <- printLine(s"\n=== Order Book for $stock ===")
                            _ <-
                              if (buyOrders.nonEmpty) {
                                val buys = buyOrders
                                  .map(o => s"${o.quantity}@${o.price}")
                                  .mkString(", ")
                                printLine(s"Buy orders: $buys")
                              } else printLine("No buy orders")
                            _ <-
                              if (sellOrders.nonEmpty) {
                                val sells = sellOrders
                                  .map(o => s"${o.quantity}@${o.price}")
                                  .mkString(", ")
                                printLine(s"Sell orders: $sells")
                              } else printLine("No sell orders")
                          } yield ()
                      }
                  } yield true

                case cmd if cmd.startsWith("cancel ") =>
                  val orderIdStr = input.trim.substring(7)
                  val result =
                    try {
                      val orderId = orderIdStr.toLong
                      for {
                        cancelled <- cancelOrder(orderId).commit
                        _ <- if (cancelled)
                               printLine(s"✓ Order $orderId cancelled")
                             else {
                               printLine(s"✗ Order $orderId not found")
                             }
                      } yield ()
                    } catch {
                      case _: Exception =>
                        printLine(s"Invalid order ID: $orderIdStr")
                    }
                  result.as(true)

                case cmd if cmd.startsWith("update ") =>
                  val parts =
                    input.trim.substring(7).split("\\s+")
                  val result =
                    try {
                      if (parts.length < 1)
                        printLine(
                          "Usage: update <ORDER_ID> [<NEW_PRICE>] [<NEW_QUANTITY>]"
                        )
                      else {
                        val orderId = parts(0).toLong
                        val newPrice =
                          if (parts.length > 1 && parts(1) != "-")
                            Some(parts(1).toDouble)
                          else None
                        val newQuantity =
                          if (parts.length > 2 && parts(2) != "-")
                            Some(parts(2).toInt)
                          else
                            None

                        for {
                          updated <-
                            updateOrder(orderId, newPrice, newQuantity).commit
                          _ <-
                            if (updated) {
                              val priceStr = newPrice
                                .map(p => s"price: $p")
                                .getOrElse(
                                  "price: unchanged"
                                )
                              val qtyStr = newQuantity
                                .map(q => s"quantity: $q")
                                .getOrElse(
                                  "quantity: unchanged"
                                )
                              printLine(
                                s"✓ Order $orderId updated ($priceStr, $qtyStr)"
                              )
                            } else
                              printLine(s"✗ Order $orderId not found")
                        } yield ()
                      }
                    } catch {
                      case _: Exception =>
                        printLine(
                          "Invalid update format. Usage: update <ORDER_ID> [<NEW_PRICE>] [<NEW_QUANTITY>]"
                        )
                    }
                  result.as(true)

                case cmd if cmd.startsWith("buy ") || cmd.startsWith("sell ") =>
                  for {
                    now     <- ZIO.clockWith(_.instant.map(_.toEpochMilli))
                    orderId <- nextOrderId.updateAndGet(_ + 1)
                    _ <- parseOrder(input, orderId, now) match {
                           case None =>
                             printLine(s"Invalid order format: $input") *>
                               printLine(
                                 "Use: buy|sell <STOCK> <PRICE> <QUANTITY>"
                               )
                           case Some(order) =>
                             for {
                               _ <- placeOrder(order)
                               side =
                                 if (order.isBuy) "Buy"
                                 else "Sell"
                               _ <-
                                 printLine(
                                   s"✓ Order placed: $side ${order.quantity} ${order.stock} @ ${order.price} (ID: ${order.id})"
                                 )
                             } yield ()
                         }
                  } yield true

                case "" =>
                  ZIO.succeed(true)

                case _ =>
                  printLine(
                    s"Unknown command: '$input'. Type 'help' for available commands."
                  )
                    .as(true)
              }

            _ <- if (shouldContinue) loop else ZIO.unit
          } yield ()

        loop
      }

      private def parseOrder(
        input: String,
        orderId: Long,
        now: Timestamp
      ): Option[Order] = {
        val parts = input.trim.split("\\s+")
        if (parts.length < 4) None
        else {
          try {
            val side     = parts(0).toLowerCase
            val stock    = parts(1).toUpperCase
            val price    = parts(2).toDouble
            val quantity = parts(3).toInt

            if (price <= 0 || quantity <= 0) None
            else {
              val isBuy = side == "buy"
              if (isBuy || side == "sell") {
                Some(
                  Order(
                    id = orderId,
                    stock = stock,
                    price = price,
                    quantity = quantity,
                    isBuy = isBuy,
                    timestamp = now
                  )
                )
              } else None
            }
          } catch {
            case _: Exception => None
          }
        }
      }

      private def showHelp(): UIO[Unit] =
        printLine(
          """
            |=== Trading System Console ===
            |Commands:
            |  buy <STOCK> <PRICE> <QUANTITY>        - Place a buy order
            |  sell <STOCK> <PRICE> <QUANTITY>       - Place a sell order
            |  cancel <ORDER_ID>                      - Cancel an order by ID
            |  update <ORDER_ID> [PRICE] [QUANTITY]  - Update order (use '-' to keep current value)
            |  book <STOCK>                           - Show order book for stock
            |  trades                                 - Show all executed trades
            |  help                                   - Show this message
            |  exit                                   - Stop the system
            |
            |Examples:
            |  buy GOLD 150 100
            |  sell SILVER 300 50
            |  cancel 1
            |  update 1 155 -
            |  update 2 - 75
            |  book OIL
            |""".stripMargin
        )

    }

    object TradingSystem {

      /**
       * Create and start a new trading system
       *   - Spawns matcher supervisor (watches for new order books)
       *   - Spawns console reader (places/cancels/updates orders interactively)
       *   - Both run concurrently within the scope
       *   - When scope closes, both are automatically cleaned up
       */
      def make: ZIO[Scope, Nothing, TradingSystem] =
        for {
          orderBooksRef    <- TMap.empty[Stock, OrderBook].commit
          tradesRef        <- TRef.make(List.empty[Trade]).commit
          handledStocksRef <- TSet.empty[Stock].commit
          matchersRef      <- Ref.make(Map.empty[Stock, Fiber[Nothing, Unit]])
          nextOrderIdRef   <- Ref.make(0L)

          system = TradingSystem(
                     orderBooksRef,
                     tradesRef,
                     handledStocksRef,
                     matchersRef,
                     nextOrderIdRef
                   )

          _ <- system.startMatcherSupervisor race system.startConsoleReader
        } yield system
    }

    object TradingSystemExample extends ZIOAppDefault {
      def run = TradingSystem.make
    }

  }

}
