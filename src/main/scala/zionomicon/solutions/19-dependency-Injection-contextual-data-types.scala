package zionomicon.solutions

package DependencyInjectionContextualDataTypes {

  /**
   *   1. Implement a simple transactional key-value store that:
   *      \index{Transactional Key-value Store}
   *
   *   - Supports basic operations (get, put, delete)
   *   - Uses ZIO environment to mark transactional effects
   *   - Provides atomic execution of multiple operations
   *   - Implements proper rollback on failure
   *
   * Sample interface:
   *
   * {{{
   * trait KVStore[k, V] {
   *   def get[V](key: K): ZIO[Transaction, Throwable, Option[V]]
   *   def put(key: K, value: V): ZIO[Transaction, Throwable, Unit]
   *   def delete(key: K): ZIO[Transaction, Throwable, Unit]
   * }
   * }}}
   */
  package TransactionalKeyValueStoreImpl {
    package kvstore {
      import zio._

      // Operation ADT - records what operations were requested
      sealed trait Operation[+K, +V]
      object Operation {
        case class Put[K, V](key: K, value: V) extends Operation[K, V]
        case class Delete[K](key: K)           extends Operation[K, Nothing]
      }

      trait TransactionManager[Transaction] {
        def run[R: Tag, E >: Throwable, A](
          zio: ZIO[R with Transaction, E, A]
        ): ZIO[R, E, A]
      }

      class Transaction[K, V] private[kvstore] (
        private[kvstore] val snapshot: Map[K, V],
        private[kvstore] val operations: Ref[List[Operation[K, V]]]
      ) {
        // Compute current state by replaying operations on snapshot
        private[kvstore] def currentState: UIO[Map[K, V]] =
          operations.get.map { ops =>
            ops.foldLeft(snapshot) {
              case (state, Operation.Put(k, v)) => state + (k -> v)
              case (state, Operation.Delete(k)) => state - k
            }
          }
      }

      object Transaction {
        private[kvstore] def make[K, V](
          snapshot: Map[K, V]
        ): UIO[Transaction[K, V]] =
          Ref.make(List.empty[Operation[K, V]]).map { ops =>
            new Transaction(snapshot, ops)
          }
      }

      trait KVStore[K, V] {
        def get(key: K): ZIO[Transaction[K, V], Throwable, Option[V]]
        def put(key: K, value: V): ZIO[Transaction[K, V], Throwable, Unit]
        def delete(key: K): ZIO[Transaction[K, V], Throwable, Unit]
      }

      class InMemoryKVStore[K: Tag, V: Tag] extends KVStore[K, V] {

        def get(key: K): ZIO[Transaction[K, V], Throwable, Option[V]] =
          ZIO.serviceWithZIO[Transaction[K, V]] { tx =>
            // Compute current view by replaying all operations
            tx.currentState.map(_.get(key))
          }

        def put(key: K, value: V): ZIO[Transaction[K, V], Throwable, Unit] =
          ZIO.serviceWithZIO[Transaction[K, V]] { tx =>
            // Just record the operation
            tx.operations.update(_ :+ Operation.Put(key, value))
          }

        def delete(key: K): ZIO[Transaction[K, V], Throwable, Unit] =
          ZIO.serviceWithZIO[Transaction[K, V]] { tx =>
            // Just record the operation
            tx.operations.update(_ :+ Operation.Delete(key))
          }
      }

      class InMemoryKVStoreTransactionManager[K: Tag, V: Tag](
        store: Ref[Map[K, V]]
      ) extends TransactionManager[Transaction[K, V]] {

        def run[R: Tag, E >: Throwable, A](
          zio: ZIO[R with Transaction[K, V], E, A]
        ): ZIO[R, E, A] =
          ZIO.acquireReleaseWith(
            acquire = for {
              snapshot <- store.get
              tx       <- Transaction.make(snapshot)
            } yield tx
          )(
            release = _ => ZIO.unit
          )(
            use = { tx =>
              zio
                .provideSome[R](ZLayer.succeed(tx))
                .tapBoth(
                  _ => rollback(tx),
                  _ => commit(tx)
                )
            }
          )

        // Simple case: only Transaction in environment
        def execute[E >: Throwable, A](
          zio: ZIO[Transaction[K, V], E, A]
        ): ZIO[Any, E, A] = run[Any, E, A](zio)

        // With additional environment: specify R explicitly
        def executeWith[R]: ExecuteWithEnv[R] = new ExecuteWithEnv[R]

        class ExecuteWithEnv[R] {
          def apply[E >: Throwable, A](
            zio: ZIO[R with Transaction[K, V], E, A]
          )(implicit tag: Tag[R]): ZIO[R, E, A] = run[R, E, A](zio)
        }

        // Automatic retry on conflict
        def runWithRetry[R: Tag, E >: Throwable, A](
          zio: ZIO[R with Transaction[K, V], E, A]
        ): ZIO[R, E, A] =
          run[R, E, A](zio).eventually

        // Convenience method for simple transactions with retry
        def executeWithRetry[E >: Throwable, A](
          zio: ZIO[Transaction[K, V], E, A]
        ): ZIO[Any, E, A] = runWithRetry[Any, E, A](zio)

        private def commit(tx: Transaction[K, V]): ZIO[Any, Throwable, Unit] =
          for {
            ops <- tx.operations.get
            // read-only transactions don't need conflict detection
            _ <- if (ops.isEmpty) {
                   ZIO.unit
                 } else {
                   // Apply all operations atomically using store.modify
                   store.modify { current =>
                     // Extract keys that this transaction modified
                     val writtenKeys = ops.collect {
                       case Operation.Put(k, _) => k
                       case Operation.Delete(k) => k
                     }.toSet

                     // Check if any written key was modified by another transaction
                     val hasConflict = writtenKeys.exists { key =>
                       current.get(key) != tx.snapshot.get(key)
                     }

                     if (hasConflict) {
                       (
                         Left(
                           new Exception(
                             "Transaction conflict detected: store was modified by another transaction"
                           )
                         ),
                         current
                       )
                     } else {
                       val newState = ops.foldLeft(current) {
                         case (state, Operation.Put(k, v)) => state + (k -> v)
                         case (state, Operation.Delete(k)) => state - k
                       }
                       (Right(()), newState)
                     }
                   }.flatMap(ZIO.fromEither(_))
                 }
          } yield ()

        private def rollback(tx: Transaction[K, V]): ZIO[Any, Nothing, Unit] =
          tx.operations.get.ignore
      }

      object InMemoryKVStoreTransactionManager {

        def make[K: Tag, V: Tag]: UIO[InMemoryKVStoreTransactionManager[K, V]] =
          Ref
            .make(Map.empty[K, V])
            .map(new InMemoryKVStoreTransactionManager(_))
      }

      object TransactionalKVStoreExample extends ZIOAppDefault {
        val program: ZIO[Any, Throwable, Unit] = {
          val kvStore = new InMemoryKVStore[String, Int]

          for {
            tm <- InMemoryKVStoreTransactionManager.make[String, Int]

            _ <- Console.printLine(
                   "\n=== Transaction 1: Simple with multiple operations ==="
                 )
            _ <- tm.execute {
                   for {
                     _ <- kvStore.put("alice", 100)
                     _ <- kvStore.put("bob", 50)
                     // Read within the same transaction sees uncommitted writes
                     aliceBalance <- kvStore.get("alice")
                     _ <- Console.printLine(
                            s"Alice balance (within tx): $aliceBalance"
                          )
                     _ <- kvStore.put("alice", 150) // Update alice
                     _ <- kvStore.delete("bob")     // Delete bob
                   } yield ()
                 }

            _ <- Console.printLine("\n=== Transaction 2: With environment ===")
            _ <- tm.executeWith[Int] {
                   for {
                     multiplier <- ZIO.service[Int]
                     _          <- Console.printLine(s"Using multiplier: $multiplier")
                     _          <- kvStore.put("carol", 10 * multiplier)
                     _          <- kvStore.put("dave", 20 * multiplier)
                     _ <-
                       Console.printLine(
                         s"Operations recorded: PUT(carol,${10 * multiplier}), PUT(dave,${20 * multiplier})"
                       )
                   } yield ()
                 }.provide(ZLayer.succeed(5))

            // Verify final state
            _ <- Console.printLine("\n=== Verifying final state ===")
            _ <- tm.execute {
                   for {
                     alice <- kvStore.get("alice")
                     bob   <- kvStore.get("bob")
                     carol <- kvStore.get("carol")
                     dave  <- kvStore.get("dave")
                     _ <- Console.printLine(
                            s"alice=$alice, bob=$bob, carol=$carol, dave=$dave"
                          )
                   } yield ()
                 }
          } yield ()
        }
        def run = program
      }

      import zio.test._

      object TransactionalKVStoreSpec extends ZIOSpecDefault {

        def spec = suite("TransactionalKVStore")(
          suite("Basic Operations")(
            test("put and get within same transaction") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]
                result <- tm.execute {
                            for {
                              _     <- kvStore.put("key1", 100)
                              value <- kvStore.get("key1")
                            } yield value
                          }
              } yield assertTrue(result.contains(100))
            },
            test("get returns None for non-existent key") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]
                result <- tm.execute(kvStore.get("missing"))
              } yield assertTrue(result == None)
            },
            test("delete removes a key") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]
                result <- tm.execute {
                            for {
                              _      <- kvStore.put("key1", 100)
                              before <- kvStore.get("key1")
                              _      <- kvStore.delete("key1")
                              after  <- kvStore.get("key1")
                            } yield (before, after)
                          }
              } yield assertTrue(result == (Some(100), None))
            },
            test("multiple puts to same key keeps last value") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]
                result <- tm.execute {
                            for {
                              _     <- kvStore.put("key1", 1)
                              _     <- kvStore.put("key1", 2)
                              _     <- kvStore.put("key1", 3)
                              value <- kvStore.get("key1")
                            } yield value
                          }
              } yield assertTrue(result.contains(3))
            },
            test("operations are visible across transactions after commit") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]
                _      <- tm.execute(kvStore.put("key1", 42))
                result <- tm.execute(kvStore.get("key1"))
              } yield assertTrue(result.contains(42))
            }
          ),
          suite("Transaction Isolation")(
            test("uncommitted changes are not visible to other transactions") {
              for {
                tm      <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore  = new InMemoryKVStore[String, Int]
                latch1  <- Promise.make[Nothing, Unit]
                latch2  <- Promise.make[Nothing, Unit]
                results <- Promise.make[Nothing, Option[Int]]

                // Transaction 1: Put a value but wait before completing
                fiber1 <- tm.execute {
                            for {
                              _ <- kvStore.put("key1", 100)
                              _ <-
                                latch1.succeed(()) // Signal that we've written
                              _ <- latch2.await // Wait for signal to proceed
                            } yield ()
                          }.fork

                // Transaction 2: Try to read while tx1 is in progress
                fiber2 <- (for {
                            _      <- latch1.await       // Wait for tx1 to write
                            result <- tm.execute(kvStore.get("key1"))
                            _      <- results.succeed(result)
                            _      <- latch2.succeed(()) // Allow tx1 to complete
                          } yield ()).fork

                _      <- fiber1.join
                _      <- fiber2.join
                result <- results.await
              } yield assertTrue(result.isEmpty)
            },
            test(
              "snapshot isolation - later transaction conflicts with earlier"
            ) {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                // Setup initial state
                _ <- tm.execute(kvStore.put("balance", 1000))

                txStarted <- Promise.make[Nothing, Unit]
                tx1Done   <- Promise.make[Nothing, Unit]

                fiber1 <- tm.execute {
                            for {
                              read1 <- kvStore.get("balance")
                              _     <- txStarted.succeed(())
                              _     <- tx1Done.await // Wait for tx2 to complete
                              _ <-
                                kvStore.put("balance", read1.getOrElse(0) + 100)
                            } yield read1
                          }.fork

                _ <- txStarted.await
                _ <- tm.execute(kvStore.put("balance", 2000))
                _ <- tx1Done.succeed(())

                result <- fiber1.join.either
              } yield
                // tx1 should fail because tx2 modified the store after tx1 took its snapshot
                assertTrue(result.isLeft)
            }
          ),
          suite("Conflict Detection")(
            test("concurrent modifications cause exactly one conflict") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                // Setup initial state
                _ <- tm.execute(kvStore.put("counter", 0))

                barrier <- Promise.make[Nothing, Unit]
                ready1  <- Promise.make[Nothing, Unit]
                ready2  <- Promise.make[Nothing, Unit]

                // Two transactions trying to increment the same counter
                fiber1 <- tm.execute {
                            for {
                              v <- kvStore.get("counter")
                              _ <- ready1.succeed(())
                              _ <- barrier.await
                              _ <- kvStore.put("counter", v.getOrElse(0) + 1)
                            } yield "tx1"
                          }.fork

                fiber2 <- tm.execute {
                            for {
                              v <- kvStore.get("counter")
                              _ <- ready2.succeed(())
                              _ <- barrier.await
                              _ <- kvStore.put("counter", v.getOrElse(0) + 10)
                            } yield "tx2"
                          }.fork

                // Wait for both to be ready, then release
                _ <- ready1.await
                _ <- ready2.await
                _ <- barrier.succeed(())

                result1 <- fiber1.join.either
                result2 <- fiber2.join.either

                // Exactly one should succeed, one should fail
                successCount = List(result1, result2).count(_.isRight)
              } yield assertTrue(successCount == 1)
            }
          ),
          suite("Rollback Behavior")(
            test("failed transaction does not modify store") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                // Setup
                _ <- tm.execute(kvStore.put("key1", 100))

                // Transaction that fails after modification
                _ <- tm.execute {
                       for {
                         _ <- kvStore.put("key1", 999)
                         _ <- kvStore.put("key2", 888)
                         _ <-
                           ZIO.fail(new RuntimeException("Intentional failure"))
                       } yield ()
                     }.either

                // Verify original state is preserved
                result <- tm.execute {
                            for {
                              v1 <- kvStore.get("key1")
                              v2 <- kvStore.get("key2")
                            } yield (v1, v2)
                          }
              } yield assertTrue(result == (Some(100), None))
            }
          ),
          suite("Stress Tests")(
            test("many sequential transactions") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                _ <- ZIO.foreach(1 to 100) { i =>
                       tm.execute(kvStore.put("counter", i))
                     }

                result <- tm.execute(kvStore.get("counter"))
              } yield assertTrue(result.contains(100))
            },
            test("high contention - at least one succeeds") {
              for {
                tm         <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore     = new InMemoryKVStore[String, Int]
                successRef <- Ref.make(0)
                failRef    <- Ref.make(0)

                // Many transactions trying to modify the same key concurrently
                fiber <- ZIO
                           .foreachParDiscard(1 to 50) { i =>
                             tm.execute {
                               for {
                                 _ <- kvStore.put("hotkey", i)
                               } yield ()
                             }.foldZIO(
                               _ => failRef.update(_ + 1),
                               _ => successRef.update(_ + 1)
                             )
                           }
                           .fork

                _ <- fiber.join

                successes <- successRef.get
                failures  <- failRef.get
              } yield assertTrue(successes >= 1) &&
                assertTrue(successes + failures == 50)
            },
            test("concurrent increments with retry") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                _ <- tm.execute(kvStore.put("counter", 0))

                fiber <-
                  ZIO
                    .foreachParDiscard(1 to 10) { _ =>
                      tm.executeWithRetry {
                        for {
                          current <- kvStore.get("counter")
                          _ <- kvStore.put(
                                 "counter",
                                 current.getOrElse(0) + 1
                               )
                        } yield ()
                      }
                    }
                    .fork

                _ <- fiber.join

                result <- tm.execute(kvStore.get("counter"))
              } yield assertTrue(result.contains(10))
            }
          ),
          suite("Edge Cases")(
            test("empty transaction commits successfully") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                result <- tm.execute(ZIO.succeed(42))
              } yield assertTrue(result == 42)
            },
            test("transaction with only reads commits successfully") {
              for {
                tm     <- InMemoryKVStoreTransactionManager.make[String, Int]
                kvStore = new InMemoryKVStore[String, Int]

                _ <- tm.execute(kvStore.put("key", 100))

                result <- tm.execute {
                            for {
                              v1 <- kvStore.get("key")
                              v2 <- kvStore.get("missing")
                            } yield (v1, v2)
                          }
              } yield assertTrue(result == (Some(100), None))
            }
          )
        )
      }

    }
  }

  /**
   *   2. Implement a distributed transaction manager using the Saga pattern
   *      discussed in the chapter by encoding the transaction into the ZIO
   *      environment. Each business action must have a corresponding
   *      compensating action that can roll back its effects. The transaction
   *      manager should compose these action-compensation pairs into a single
   *      atomic transaction, ensuring that either all actions are completed
   *      successfully or all completed actions are rolled back through their
   *      compensating actions.
   */
  package DistributedTransactionManagerImpl {

    import zio._

    /**
     * Represents a saga transaction step with both a forward action and a
     * compensating action. The forward action executes the business logic,
     * while the compensating action undoes the effects if the saga needs to be
     * rolled back.
     */
    case class SagaStep[R, E, A](
      name: String,
      action: ZIO[R, E, A],
      compensation: A => ZIO[R, Nothing, Unit]
    )

    object SagaStep {

      /**
       * Create a saga step with a named action and compensation.
       */
      def apply[R, E, A](
        name: String,
        action: ZIO[R, E, A],
        compensation: A => ZIO[R, Nothing, Unit]
      ): SagaStep[R, E, A] =
        new SagaStep(name, action, compensation)

      /**
       * Create a saga step where compensation doesn't need the action result.
       */
      def simple[R, E](
        name: String,
        action: ZIO[R, E, Unit],
        compensation: ZIO[R, Nothing, Unit]
      ): SagaStep[R, E, Unit] =
        SagaStep(name, action, _ => compensation)
    }

    /**
     * Internal state tracking executed steps and their compensations.
     */
    case class ExecutedStep[R](
      name: String,
      compensate: ZIO[R, Nothing, Unit]
    )

    /**
     * The SagaTransaction service tracks the execution state of a saga,
     * maintaining the stack of completed steps for potential rollback.
     */
    trait SagaTransaction {

      /**
       * Execute a saga step, recording it for potential compensation.
       */
      def executeStep[R, E, A](step: SagaStep[R, E, A]): ZIO[R, E, A]

      /**
       * Get the history of executed steps (for debugging/monitoring).
       */
      def getHistory: UIO[List[String]]

      /**
       * Internal method to get the executed steps for compensation. This is
       * only used by the SagaTransactionManager.
       */
      private[DistributedTransactionManagerImpl] def getExecutedSteps
        : UIO[List[ExecutedStep[Any]]]
    }

    object SagaTransaction {

      /**
       * Reference-based implementation of SagaTransaction. Tracks executed
       * steps in the order they were executed.
       */
      private[DistributedTransactionManagerImpl] final case class SagaTransactionImpl(
        executedSteps: Ref[List[ExecutedStep[Any]]]
      ) extends SagaTransaction {

        def executeStep[R1, E, A](step: SagaStep[R1, E, A]): ZIO[R1, E, A] =
          for {
            _      <- ZIO.logDebug(s"Executing saga step: ${step.name}")
            result <- step.action
            _ <-
              executedSteps.update { steps =>
                ExecutedStep(
                  step.name,
                  step
                    .compensation(result)
                    .asInstanceOf[ZIO[Any, Nothing, Unit]]
                ) :: steps
              }
            _ <- ZIO.logDebug(s"Successfully executed saga step: ${step.name}")
          } yield result

        def getHistory: UIO[List[String]] =
          executedSteps.get.map(_.reverse.map(_.name))

        def getExecutedSteps: UIO[List[ExecutedStep[Any]]] =
          executedSteps.get.map(_.asInstanceOf[List[ExecutedStep[Any]]])
      }

      object SagaTransactionImpl {
        def make(
          ref: Ref[List[ExecutedStep[Any]]]
        ): SagaTransaction =
          new SagaTransactionImpl(ref)

        def empty: UIO[SagaTransaction] =
          Ref
            .make[List[ExecutedStep[Any]]](List.empty)
            .map(SagaTransactionImpl(_))
      }

      /**
       * Access the current SagaTransaction from the environment.
       */
      val get: URIO[SagaTransaction, SagaTransaction] =
        ZIO.service[SagaTransaction]

      /**
       * Execute a saga step using the transaction from the environment.
       */
      def executeStep[R, E, A](
        step: SagaStep[R, E, A]
      ): ZIO[R with SagaTransaction, E, A] =
        ZIO.serviceWithZIO[SagaTransaction](_.executeStep(step))

      /**
       * Get the execution history from the current transaction.
       */
      def getHistory: URIO[SagaTransaction, List[String]] =
        ZIO.serviceWithZIO[SagaTransaction](_.getHistory)

      /**
       * Internal method to compensate all executed steps in reverse order.
       */
      private[DistributedTransactionManagerImpl] def compensateAll(
        transaction: SagaTransaction
      ): ZIO[Any, Nothing, Unit] =
        transaction.getExecutedSteps.flatMap { steps =>
          ZIO.debug(s"Rolling back ${steps.size} saga steps") *>
            ZIO.foreachDiscard(steps) { step =>
              ZIO.logDebug(s"Compensating step: ${step.name}") *>
                step.compensate.catchAllCause(cause =>
                  ZIO.logError(s"Compensation failed for ${step.name}: $cause")
                )
            }
        }

      val live: ULayer[SagaTransaction] =
        ZLayer {
          Ref
            .make[List[ExecutedStep[Any]]](List.empty)
            .map(SagaTransactionImpl(_))
        }
    }

    /**
     * The SagaTransactionManager orchestrates saga execution with automatic
     * rollback.
     */
    trait SagaTransactionManager {

      /**
       * Execute a saga transaction. If any step fails, all completed steps are
       * compensated in reverse order.
       */
      def executeTransaction[R, E, A](
        saga: ZIO[R with SagaTransaction, E, A]
      ): ZIO[R, E, A]
    }

    object SagaTransactionManager {
      final case class LiveSagaTransactionManager()
          extends SagaTransactionManager {

        def executeTransaction[R, E, A](
          saga: ZIO[R with SagaTransaction, E, A]
        ): ZIO[R, E, A] =
          for {
            _           <- ZIO.debug("Starting saga transaction")
            transaction <- SagaTransaction.SagaTransactionImpl.empty
            env         <- ZIO.environment[R]
            result <-
              saga
                .provideEnvironment(env.add[SagaTransaction](transaction))
                .tapError { error =>
                  ZIO.logWarning(s"Saga transaction failed: $error") *>
                    SagaTransaction.compensateAll(transaction)
                }
            _ <- ZIO.debug("Saga transaction completed successfully")
          } yield result
      }

      /**
       * Execute a saga transaction using the manager from the environment. This
       * is a convenience method that extracts the manager from the environment
       * and delegates to its executeTransaction method.
       */
      def executeTransaction[R, E, A](
        saga: ZIO[R with SagaTransaction, E, A]
      ): ZIO[R with SagaTransactionManager, E, A] =
        ZIO.serviceWithZIO[SagaTransactionManager](
          _.executeTransaction[R, E, A](saga)
        )

      val live: ULayer[SagaTransactionManager] =
        ZLayer.succeed(LiveSagaTransactionManager())
    }

    // ============================================================================
    // Example Domain Services
    // ============================================================================

    object types {
      type TransactionId   = String
      type ReservationId   = String
      type ShipmentId      = String
      type OrderId         = String
      type ProductId       = String
      type ShipmentAddress = String
    }
    import types._

    /**
     * Example: Payment service that can charge and refund.
     */
    trait PaymentService {
      def charge(
        orderId: OrderId,
        amount: BigDecimal
      ): IO[String, TransactionId]
      def refund(transactionId: TransactionId): UIO[Unit]
    }

    object PaymentService {
      case class InMemoryPaymentService(
        transactions: Ref[Map[TransactionId, (OrderId, BigDecimal)]]
      ) extends PaymentService {

        def charge(
          orderId: OrderId,
          amount: BigDecimal
        ): IO[String, TransactionId] =
          for {
            _             <- ZIO.debug(s"Charging $amount for order $orderId")
            _             <- ZIO.sleep(100.millis) // Simulate network call
            transactionId <- Random.nextUUID.map(_.toString)
            _             <- transactions.update(_ + (transactionId -> (orderId, amount)))
            _             <- ZIO.debug(s"Payment successful: $transactionId")
          } yield transactionId

        def refund(transactionId: TransactionId): UIO[Unit] =
          for {
            _    <- ZIO.debug(s"Refunding transaction $transactionId")
            _    <- ZIO.sleep(100.millis)
            txns <- transactions.get
            _ <- ZIO.foreachDiscard(txns.get(transactionId)) {
                   case (orderId, amount) =>
                     ZIO.debug(s"Refunded $amount for order $orderId")
                 }
            _ <- transactions.update(_ - transactionId)
          } yield ()
      }

      val live: ULayer[PaymentService] =
        ZLayer {
          Ref
            .make(Map.empty[String, (String, BigDecimal)])
            .map(InMemoryPaymentService(_))
        }

      def charge(
        orderId: String,
        amount: BigDecimal
      ): ZIO[PaymentService, String, TransactionId] =
        ZIO.serviceWithZIO[PaymentService](_.charge(orderId, amount))

      def refund(transactionId: TransactionId): URIO[PaymentService, Unit] =
        ZIO.serviceWithZIO[PaymentService](_.refund(transactionId))
    }

    /**
     * Example: Inventory service that can reserve and release stock.
     */
    trait InventoryService {
      def reserve(
        productId: ProductId,
        quantity: Int
      ): IO[String, ReservationId]
      def release(reservationId: ReservationId): UIO[Unit]
    }

    object InventoryService {
      case class InMemoryInventoryService(
        inventory: Ref[Map[ProductId, Int]],
        reservations: Ref[Map[ReservationId, (ProductId, Int)]]
      ) extends InventoryService {

        def reserve(
          productId: ProductId,
          quantity: Int
        ): IO[String, ReservationId] =
          for {
            _     <- ZIO.debug(s"Reserving $quantity of product $productId")
            _     <- ZIO.sleep(100.millis)
            stock <- inventory.get.map(_.getOrElse(productId, 0))
            _ <- ZIO
                   .fail(s"Insufficient stock for $productId")
                   .when(stock < quantity)
            reservationId <- Random.nextUUID.map(_.toString)
            _ <-
              inventory.update(inv => inv + (productId -> (stock - quantity)))
            _ <-
              reservations.update(_ + (reservationId -> (productId, quantity)))
            _ <- ZIO.debug(s"Reservation successful: $reservationId")
          } yield reservationId

        def release(reservationId: ReservationId): UIO[Unit] =
          for {
            _   <- ZIO.debug(s"Releasing reservation $reservationId")
            _   <- ZIO.sleep(100.millis)
            res <- reservations.get
            _ <- ZIO.foreachDiscard(res.get(reservationId)) {
                   case (productId, quantity) =>
                     inventory.update(inv =>
                       inv + (productId -> (inv
                         .getOrElse(productId, 0) + quantity))
                     ) *> ZIO.debug(
                       s"Released $quantity of product $productId"
                     )
                 }
            _ <- reservations.update(_ - reservationId)
          } yield ()
      }

      def withInitialStock(
        stock: Map[ProductId, Int]
      ): ULayer[InventoryService] =
        ZLayer {
          for {
            inventory    <- Ref.make(stock)
            reservations <- Ref.make(Map.empty[String, (String, Int)])
          } yield InMemoryInventoryService(inventory, reservations)
        }

      val live: ULayer[InventoryService] = withInitialStock(Map.empty)

      def reserve(
        productId: ProductId,
        quantity: Int
      ): ZIO[InventoryService, String, ReservationId] =
        ZIO.serviceWithZIO[InventoryService](_.reserve(productId, quantity))

      def release(reservationId: ReservationId): URIO[InventoryService, Unit] =
        ZIO.serviceWithZIO[InventoryService](_.release(reservationId))
    }

    /**
     * Example: Shipping service that can schedule and cancel shipments.
     */
    trait ShippingService {
      def schedule(
        orderId: OrderId,
        address: ShipmentAddress
      ): IO[String, ShipmentId]
      def cancel(shipmentId: ShipmentId): UIO[Unit]
    }

    object ShippingService {
      case class InMemoryShippingService(
        shipments: Ref[Map[ShipmentId, (OrderId, ShipmentAddress)]]
      ) extends ShippingService {

        def schedule(
          orderId: OrderId,
          address: ShipmentAddress
        ): IO[String, ShipmentId] =
          for {
            _ <-
              ZIO.debug(s"Scheduling shipment for order $orderId to $address")
            _          <- ZIO.sleep(100.millis)
            shipmentId <- Random.nextUUID.map(_.toString)
            _          <- shipments.update(_ + (shipmentId -> (orderId, address)))
            _          <- ZIO.debug(s"Shipment scheduled: $shipmentId")
          } yield shipmentId

        def cancel(shipmentId: ShipmentId): UIO[Unit] =
          for {
            _    <- ZIO.debug(s"Cancelling shipment $shipmentId")
            _    <- ZIO.sleep(100.millis)
            shps <- shipments.get
            _ <- ZIO.foreachDiscard(shps.get(shipmentId)) {
                   case (orderId, address) =>
                     ZIO.debug(s"Cancelled shipment for order $orderId")
                 }
            _ <- shipments.update(_ - shipmentId)
          } yield ()
      }

      val live: ULayer[ShippingService] =
        ZLayer {
          Ref
            .make(Map.empty[ShipmentId, (OrderId, ShipmentAddress)])
            .map(InMemoryShippingService(_))
        }

      def schedule(
        orderId: OrderId,
        address: ShipmentAddress
      ): ZIO[ShippingService, String, ShipmentId] =
        ZIO.serviceWithZIO[ShippingService](_.schedule(orderId, address))

      def cancel(shipmentId: ShipmentId): URIO[ShippingService, Unit] =
        ZIO.serviceWithZIO[ShippingService](_.cancel(shipmentId))
    }

    // ============================================================================
    // Example Saga: Order Processing
    // ============================================================================
    case class OrderRequest(
      orderId: OrderId,
      productId: ProductId,
      quantity: Int,
      amount: BigDecimal,
      shippingAddress: ShipmentAddress
    )

    case class OrderResult(
      orderId: OrderId,
      transactionId: TransactionId,
      reservationId: ReservationId,
      shipmentId: ShipmentId
    )

    object OrderProcessingSaga {

      /**
       * Process an order using a saga pattern with automatic rollback on
       * failure.
       *
       * Steps:
       *   1. Reserve inventory
       *   2. Charge payment
       *   3. Schedule shipment
       *
       * If any step fails, all previous steps are compensated in reverse order.
       */
      def processOrder(
        request: OrderRequest
      ): ZIO[
        PaymentService
          with InventoryService
          with ShippingService
          with SagaTransaction,
        String,
        OrderResult
      ] =
        for {
          // Step 1: Reserve inventory
          reservationId <- SagaTransaction.executeStep(
                             SagaStep(
                               name = "Reserve Inventory",
                               action = InventoryService
                                 .reserve(request.productId, request.quantity),
                               compensation = (resId: String) =>
                                 InventoryService.release(resId)
                             )
                           )

          // Step 2: Charge payment
          transactionId <-
            SagaTransaction.executeStep(
              SagaStep(
                name = "Charge Payment",
                action = PaymentService.charge(request.orderId, request.amount),
                compensation =
                  (txId: TransactionId) => PaymentService.refund(txId)
              )
            )

          // Step 3: Schedule shipment
          shipmentId <- SagaTransaction.executeStep(
                          SagaStep(
                            name = "Schedule Shipment",
                            action = ShippingService.schedule(
                              request.orderId,
                              request.shippingAddress
                            ),
                            compensation = (shipId: ShipmentId) =>
                              ShippingService.cancel(shipId)
                          )
                        )

          _ <- ZIO.debug(s"Order ${request.orderId} processed successfully")

        } yield OrderResult(
          request.orderId,
          transactionId,
          reservationId,
          shipmentId
        )
    }

    // ============================================================================
    // Example Application & Tests
    // ============================================================================
    object SagaExamples extends ZIOAppDefault {

      /**
       * Example 1: Successful saga execution
       */
      val successfulOrder: ZIO[
        SagaTransactionManager
          with PaymentService
          with InventoryService
          with ShippingService,
        String,
        OrderResult
      ] = {
        val request = OrderRequest(
          orderId = "ORDER-001",
          productId = "PROD-123",
          quantity = 2,
          amount = BigDecimal(99.99),
          shippingAddress = "123 Main St"
        )

        SagaTransactionManager.executeTransaction[
          PaymentService with InventoryService with ShippingService,
          String,
          OrderResult
        ](
          OrderProcessingSaga.processOrder(request)
        )
      }

      /**
       * Example 2: Saga with failure in payment step (requires rollback)
       */
      val failedPaymentOrder: ZIO[
        SagaTransactionManager
          with PaymentService
          with InventoryService
          with ShippingService,
        String,
        OrderResult
      ] = {
        val request = OrderRequest(
          orderId = "ORDER-002",
          productId = "PROD-456",
          quantity = 1,
          amount = BigDecimal(199.99),
          shippingAddress = "456 Oak Ave"
        )

        // Inject a payment service that will fail
        val failingPaymentService = ZLayer.succeed(
          new PaymentService {
            def charge(
              orderId: String,
              amount: BigDecimal
            ): IO[String, String] =
              ZIO.debug(s"Payment service failing for order $orderId") *>
                ZIO.sleep(100.millis) *>
                ZIO.fail("Payment gateway timeout")

            def refund(transactionId: String): UIO[Unit] =
              ZIO.debug(
                s"Refund called for $transactionId (should not happen)"
              )
          }
        )

        SagaTransactionManager
          .executeTransaction[
            PaymentService with InventoryService with ShippingService,
            String,
            OrderResult
          ](
            OrderProcessingSaga.processOrder(request)
          )
          .provideSomeLayer[
            InventoryService with ShippingService with SagaTransactionManager
          ](failingPaymentService)
      }

      /**
       * Example 3: Saga with failure in shipping step (requires rollback of
       * inventory and payment)
       */
      val failedShippingOrder: ZIO[
        SagaTransactionManager
          with PaymentService
          with InventoryService
          with ShippingService,
        String,
        OrderResult
      ] = {
        val request = OrderRequest(
          orderId = "ORDER-003",
          productId = "PROD-789",
          quantity = 3,
          amount = BigDecimal(299.99),
          shippingAddress = "789 Pine Rd"
        )

        // Inject a shipping service that will fail
        val failingShippingService = ZLayer.succeed(
          new ShippingService {
            def schedule(orderId: String, address: String): IO[String, String] =
              ZIO.debug(s"Shipping service failing for order $orderId") *>
                ZIO.sleep(100.millis) *>
                ZIO.fail("Shipping service unavailable")

            def cancel(shipmentId: String): UIO[Unit] =
              ZIO.debug(
                s"Cancel shipment called for $shipmentId (should not happen)"
              )
          }
        )

        SagaTransactionManager
          .executeTransaction[
            PaymentService with InventoryService with ShippingService,
            String,
            OrderResult
          ](
            OrderProcessingSaga.processOrder(request)
          )
          .provideSomeLayer[
            PaymentService with InventoryService with SagaTransactionManager
          ](failingShippingService)
      }

      def run = {
        val program = for {
          _ <- ZIO.debug("=" * 80)
          _ <- ZIO.debug("Example 1: Successful order processing")
          _ <- ZIO.debug("=" * 80)
          _ <- successfulOrder

          _ <- ZIO.debug("\n" + "=" * 80)
          _ <- ZIO.debug(
                 "Example 2: Order with payment failure (rollback inventory)"
               )
          _ <- ZIO.debug("=" * 80)
          _ <- failedPaymentOrder.catchAll(err =>
                 ZIO.logError(s"Order failed as expected: $err")
               )

          _ <- ZIO.debug("\n" + "=" * 80)
          _ <-
            ZIO.debug(
              "Example 3: Order with shipping failure (rollback payment and inventory)"
            )
          _ <- ZIO.debug("=" * 80)
          _ <- failedShippingOrder.catchAll(err =>
                 ZIO.logError(s"Order failed as expected: $err")
               )

        } yield ()

        program.provide(
          SagaTransactionManager.live,
          PaymentService.live,
          InventoryService.withInitialStock(
            Map("PROD-123" -> 10, "PROD-456" -> 5, "PROD-789" -> 8)
          ),
          ShippingService.live
        )
      }
    }
  }
}
