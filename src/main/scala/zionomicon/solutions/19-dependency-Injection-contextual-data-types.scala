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
  package DistributedTransactionManagerImpl {}

}
