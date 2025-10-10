package zionomicon.solutions

package QueueWorkDistribution {

  /**
   *   1. Implement load balancer that distributes work across multiple worker
   *      queues using a round-robin strategy:
   *
   * {{{
   * trait LoadBalancer[A] {
   *   def submit(work: A): Task[Unit]
   *   def shutdown: Task[Unit]
   * }
   * object LoadBalancer {
   *   def make[A](workerCount: Int, process: A => Task[A]) = ???
   * }
   * }}}
   */
  package LoadBalancerImpl {

    import zio._

    trait LoadBalancer[A] {
      def submit(work: A): Task[Unit]

      def shutdown: Task[Unit]

      def isShutdown: UIO[Boolean]
    }

    object LoadBalancer {

      def make[A](
        workerCount: Int,
        process: A => Task[A]
      ): Task[LoadBalancer[A]] =
        for {
          workers <- ZIO.replicateZIO(workerCount)(Queue.unbounded[A])

          roundRobinCounter <- Ref.make(0)
          isShutdownFlag    <- Ref.make(false)

          totalSubmitted     <- Ref.make(0L)
          totalProcessed     <- Ref.make(0L)
          processingCounters <- ZIO.replicateZIO(workerCount)(Ref.make(0L))

          // Worker completion promises
          workerCompletions <-
            ZIO.replicateZIO(workerCount)(Promise.make[Nothing, Unit])

          // Start worker fibers with processing tracking
          workerFibers <-
            ZIO.foreach(
              workers
                .zip(processingCounters)
                .zip(workerCompletions)
                .zipWithIndex
            ) { case (((queue, counter), completion), idx) =>
              def workerLoop: UIO[Unit] =
                queue.poll.flatMap {
                  case Some(work) =>
                    for {
                      _ <- counter.update(_ + 1)
                      _ <-
                        process(work)
                          .tapError(err =>
                            ZIO.debug(s"Worker $idx failed: ${err.getMessage}")
                          )
                          .ignore
                      _ <- totalProcessed.update(_ + 1)
                      _ <- workerLoop
                    } yield ()

                  case None =>
                    isShutdownFlag.get.flatMap { shuttingDown =>
                      if (shuttingDown)
                        ZIO.debug(s"Worker $idx exiting gracefully") *>
                          completion.succeed(()).unit
                      else workerLoop
                    }
                }

              workerLoop
                .onError(cause =>
                  ZIO.debug(s"Worker $idx error: $cause") *>
                    completion.failCause(cause)
                )
                .forkDaemon
            }

        } yield new LoadBalancer[A] {

          override def submit(work: A): Task[Unit] =
            for {
              shutdown <- isShutdownFlag.get.debug("Checking shutdown status")
              _ <- ZIO.when(shutdown)(
                     ZIO.fail(
                       new IllegalStateException(
                         "LoadBalancer is shutting down"
                       )
                     )
                   )

              queueIndex <-
                roundRobinCounter.getAndUpdate(i => (i + 1) % workerCount)
              _ <- totalSubmitted.update(_ + 1)
              _ <- workers.toList(queueIndex).offer(work)
            } yield ()

          override def shutdown: Task[Unit] =
            ZIO.uninterruptibleMask { restore =>
              for {
                alreadyShutdown <- isShutdownFlag.getAndSet(true)
                _ <- ZIO.unless(alreadyShutdown) {
                       for {
                         submitted <- totalSubmitted.get
                         processed <- totalProcessed.get
                         remaining  = submitted - processed

                         _ <-
                           ZIO.debug(
                             s"Starting graceful shutdown: $remaining items remaining to process"
                           )

                         // Wait for all workers to complete processing
                         _ <- restore(ZIO.foreach(workerCompletions)(_.await))

                         // Wait for all worker fibers to complete
                         _ <- restore(ZIO.foreach(workerFibers)(_.join))

                       } yield ()
                     }
              } yield ()
            }

          override def isShutdown: UIO[Boolean] =
            isShutdownFlag.get
        }
    }

    // Example usage with graceful shutdown
    object LoadBalancerGracefulExample extends ZIOAppDefault {

      def run =
        for {
          // Create load balancer with metrics
          balancer <- LoadBalancer.make[String](
                        workerCount = 3,
                        process = (work: String) =>
                          for {
                            _ <- ZIO.debug(s"Processing: $work")

                            // Simulate variable processing time
                            delay <- Random.nextIntBetween(1000, 5000)
                            _     <- ZIO.sleep(delay.milliseconds)

                            processed = s"Completed: $work"
                            _        <- ZIO.debug(processed)
                          } yield processed
                      )

          // Submit a batch of work
          _ <- ZIO
                 .foreach(1 to 20) { i =>
                   balancer.submit(s"Task-$i").delay(100.milliseconds)
                 }
                 .fork // Submit asynchronously

          // Let some work get processed
          _ <- ZIO.sleep(500.milliseconds)

          // Try graceful shutdown
          _             <- ZIO.debug("Initiating graceful shutdown...")
          shutdownFiber <- balancer.shutdown.fork

          // Wait until shutdown starts
          _ <- balancer.isShutdown.repeatUntil(identity)

          // Try to submit more work (should fail)
          _ <- balancer.submit("Late-Task").either.flatMap {
                 case Left(e) =>
                   ZIO.debug(
                     s"Expected: Cannot submit during shutdown - ${e.getMessage}"
                   )
                 case Right(_) =>
                   ZIO.debug(
                     "Unexpected: Submission succeeded during shutdown"
                   )
               }

          // Wait for shutdown to complete
          _ <- shutdownFiber.join

          _ <- ZIO.debug("Application complete")

        } yield ()
    }

  }

  /**
   *   2. Implement a rate limiter that limits the number of requests processed
   *      in a given time frame. It takes the time interval and the maximum
   *      number of calls that are allowed to be performed within the time
   *      interval:
   *
   * {{{
   *      trait RateLimiter {
   *        def acquire: UIO[Unit]
   *        def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
   *      }
   *
   *      object RateLimiter {
   *        def make(max: Int, interval: Duration): UIO[RateLimiter] = ???
   *      }
   * }}}
   */
  package RateLimiterImpl {

    import zio._

    import java.util.concurrent.TimeUnit

    trait RateLimiter {
      def acquire: UIO[Unit]

      def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
    }

    object RateLimiter {
      def make(max: Int, interval: Duration): UIO[RateLimiter] =
        for {
          // Create a bounded queue to hold permits (tokens)
          permits <- Queue.bounded[Unit](max)
          // Initially fill the queue with max permits
          _ <- permits.offerAll(List.fill(max)(()))
        } yield new RateLimiter {

          def acquire: UIO[Unit] =
            for {
              // Take a permit from the queue (blocks if none available)
              _ <- permits.take
              // Schedule returning the permit after the interval expires
              _ <-
                (ZIO.sleep(interval) *> permits.offer(())).fork.uninterruptible
            } yield ()

          def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
            acquire *> zio
        }
    }

    object RateLimiterExample extends ZIOAppDefault {

      def run =
        for {
          // Create rate limiter: max 5 requests per 10 seconds
          rateLimiter <- RateLimiter.make(max = 5, interval = 10.seconds)

          startTime <- Clock.currentTime(TimeUnit.MILLISECONDS)

          // Submit 15 requests
          _ <- ZIO.foreach(1 to 15) { i =>
                 rateLimiter {
                   for {
                     now    <- Clock.currentTime(TimeUnit.MILLISECONDS)
                     elapsed = now - startTime
                     random <- Random.nextLongBetween(100, 500)

                     _ <- ZIO.sleep(random.milliseconds)
                     _ <- ZIO.debug(s"Request $i processed at ${elapsed}ms")
                   } yield ()
                 }
               }

          _ <- ZIO.debug("All requests completed")
        } yield ()
    }

    object RateLimiterConcurrentExample extends ZIOAppDefault {

      def run =
        for {
          // Rate limiter: 5 requests per 10 seconds
          rateLimiter <- RateLimiter.make(max = 5, interval = 10.seconds)

          startTime <- Clock.currentTime(TimeUnit.MILLISECONDS)

          // Launch 15 concurrent requests
          _ <- ZIO.foreachPar(1 to 15) { i =>
                 rateLimiter {
                   for {
                     now    <- Clock.currentTime(TimeUnit.MILLISECONDS)
                     elapsed = now - startTime
                     random <- Random.nextLongBetween(100, 500)

                     _ <- ZIO.sleep(random.milliseconds)
                     _ <- ZIO.debug(s"Request $i processed at ${elapsed}ms")
                   } yield ()
                 }
               }

          _ <- ZIO.debug("All requests completed")
        } yield ()
    }
  }

  /**
   *   3. Implement a circuit breaker that prevents calls to a service after a
   *      certain number of failures:
   *
   * {{{
   *     trait CircuitBreaker {
   *       def protect[A](operation: => Task[A]): Task[A]
   *     }
   * }}}
   */
  package CircuitBreakerImpl {
    import zio._

    import java.util.concurrent.TimeUnit

    trait CircuitBreaker {
      def protect[A](operation: Task[A]): Task[A]
      def currentState: UIO[CircuitBreaker.State]
    }

    case class CircuitBreakerOpen() extends Exception("Circuit breaker is open")

    object CircuitBreaker {
      sealed trait State {
        def failureCount: Int
      }
      object State {
        case class Closed(failureCount: Int) extends State {
          override def toString: String = "Closed"
        }
        case class Open(failureCount: Int, openedAt: Long) extends State {
          override def toString: String = "Open"
        }
      }

      def make(
        maxFailures: Int,
        resetTimeout: Duration
      ): ZIO[Any, Nothing, CircuitBreaker] =
        Ref.Synchronized
          .make[State](State.Closed(failureCount = 0))
          .map { stateRef =>
            new CircuitBreaker {
              override def currentState: UIO[State] = stateRef.get

              override def protect[A](operation: Task[A]): Task[A] =
                stateRef.modifyZIO {
                  case State.Closed(failureCount) =>
                    // Execute operation and capture result as Either
                    operation.either.flatMap {
                      case Left(error) =>
                        val newFailureCount = failureCount + 1
                        if (newFailureCount >= maxFailures) {
                          // Transition to Open state with current timestamp
                          Clock.currentTime(TimeUnit.MILLISECONDS).map { now =>
                            val newState = State.Open(
                              failureCount = newFailureCount,
                              openedAt = now
                            )
                            (Left(error), newState)
                          }
                        } else {
                          // Stay in Closed state
                          val newState =
                            State.Closed(failureCount = newFailureCount)
                          ZIO.succeed((Left(error), newState))
                        }
                      case Right(success) =>
                        // Reset failure count and stay in Closed state
                        val newState = State.Closed(failureCount = 0)
                        ZIO.succeed((Right(success), newState))
                    }.uninterruptible

                  case state @ State.Open(failureCount, openedAt) =>
                    // Check if enough time has passed to transition to implicit half-open state
                    Clock.currentTime(TimeUnit.MILLISECONDS).flatMap { now =>
                      val elapsed = now - openedAt
                      if (elapsed >= resetTimeout.toMillis) {
                        // Transition to implicit half-open state and try the operation
                        ZIO.uninterruptibleMask { restore =>
                          restore(operation).either.flatMap {
                            case Left(error) =>
                              // Transition back to Open with new timestamp
                              Clock.currentTime(TimeUnit.MILLISECONDS).map {
                                newNow =>
                                  val newState = State.Open(
                                    failureCount = failureCount,
                                    openedAt = newNow
                                  )
                                  (Left(error), newState)
                              }
                            case Right(success) =>
                              // Transition to Closed
                              val newState = State.Closed(failureCount = 0)
                              ZIO.succeed((Right(success), newState))
                          }
                        }
                      } else {
                        // Still in the timeout period, reject immediately
                        ZIO.succeed((Left(CircuitBreakerOpen()), state))
                      }
                    }

                }.absolve
            }
          }

    }

    object CircuitBreakerExample extends ZIOAppDefault {

      // Service that responds based on predetermined outcomes (true = success, false = failure)
      class TestService(outcomes: List[Boolean]) {
        private val index = new java.util.concurrent.atomic.AtomicInteger(0)

        def call: Task[String] = {
          val i = index.getAndIncrement()
          outcomes.lift(i) match {
            case Some(true) | None => ZIO.succeed("Success")
            case Some(false)       => ZIO.fail(new Exception("Service failure"))
          }
        }
      }

      // Example 1: Basic circuit breaker behavior
      def example1Basic: ZIO[Any, Nothing, Unit] =
        for {
          _      <- ZIO.debug("\n=== Example 1: Basic Behavior ===")
          _      <- ZIO.debug("Scenario: 3 consecutive failures open the circuit\n")
          cb     <- CircuitBreaker.make(maxFailures = 3, resetTimeout = 1.second)
          service = new TestService(List.fill(6)(false)) // 6 failures

          _ <- ZIO.foreach(1 to 6) { i =>
                 for {
                   state <- cb.currentState
                   _     <- ZIO.debug(s"Request $i [Circuit: $state]")
                   _ <- (cb.protect(service.call): Task[String])
                          .tap(_ => ZIO.debug(s"  ✓ Success"))
                          .tapError(_ => ZIO.debug(s"  ✗ Failed"))
                          .catchAll {
                            case _: CircuitBreakerOpen =>
                              ZIO.debug(s"  ⊗ Rejected - circuit is open")
                            case _ => ZIO.unit
                          }
                 } yield ()
               }

          finalState <- cb.currentState
          _          <- ZIO.debug(s"\nFinal state: $finalState")
        } yield ()

      // Example 2a: Successful recovery
      def example2aRecoverySuccess: ZIO[Any, Nothing, Unit] =
        for {
          _ <- ZIO.debug("\n=== Example 2a: Successful Recovery ===")
          _ <-
            ZIO.debug(
              "Scenario: Circuit opens, then successfully recovers after timeout\n"
            )
          cb     <- CircuitBreaker.make(maxFailures = 2, resetTimeout = 1.second)
          service = new TestService(List(false, false, true))

          _ <- ZIO.debug("Opening circuit with 2 failures...")
          _ <- ZIO.foreach(1 to 2) { i =>
                 cb.protect(service.call)
                   .tapError(_ => ZIO.debug(s"Request $i: ✗ Failed"))
                   .ignore
               }

          state1 <- cb.currentState
          _      <- ZIO.debug(s"Circuit state: $state1\n")

          _ <- ZIO.debug("Waiting for reset timeout (1 second)...")
          _ <- ZIO.sleep(1100.millis)

          _ <- ZIO.debug("Attempting recovery request...")
          _ <- cb.protect(service.call)
                 .tap(_ => ZIO.debug("Request 3: ✓ Success - circuit closed"))
                 .ignore

          finalState <- cb.currentState
          _          <- ZIO.debug(s"Final state: $finalState")
        } yield ()

      // Example 2b: Failed recovery then successful recovery
      def example2bRecoveryFailure: ZIO[Any, Nothing, Unit] =
        for {
          _ <- ZIO.debug("\n=== Example 2b: Failed Recovery, Then Success ===")
          _ <-
            ZIO.debug(
              "Scenario: Circuit opens, first recovery fails, second recovery succeeds\n"
            )
          cb <- CircuitBreaker.make(maxFailures = 2, resetTimeout = 500.millis)
          service = new TestService(
                      List(
                        false, // Failure 1
                        false, // Failure 2 - opens circuit
                        false, // Recovery attempt 1 fails - reopens circuit
                        true   // Recovery attempt 2 succeeds - closes circuit
                      )
                    )

          _ <- ZIO.debug("Opening circuit with 2 failures...")
          _ <- ZIO.foreach(1 to 2) { i =>
                 cb.protect(service.call)
                   .tapError(_ => ZIO.debug(s"Request $i: ✗ Failed"))
                   .ignore
               }

          state1 <- cb.currentState
          _      <- ZIO.debug(s"Circuit state: $state1\n")

          _ <- ZIO.debug("Waiting for reset timeout (500ms)...")
          _ <- ZIO.sleep(600.millis)

          _ <- ZIO.debug("First recovery attempt...")
          _ <-
            cb.protect(service.call)
              .tapError(_ => ZIO.debug("Request 3: ✗ Failed - circuit reopens"))
              .ignore

          state2 <- cb.currentState
          _      <- ZIO.debug(s"Circuit state: $state2\n")

          _ <- ZIO.debug("Waiting for reset timeout again (500ms)...")
          _ <- ZIO.sleep(600.millis)

          _ <- ZIO.debug("Second recovery attempt...")
          _ <- cb.protect(service.call)
                 .tap(_ => ZIO.debug("Request 4: ✓ Success - circuit closed"))
                 .ignore

          finalState <- cb.currentState
          _          <- ZIO.debug(s"Final state: $finalState")
        } yield ()

      // Example 3: Parallel requests with mixed outcomes
      def example3Parallel: ZIO[Any, Nothing, Unit] =
        for {
          _ <- ZIO.debug(
                 "\n=== Example 3: Parallel Requests with Mixed Outcomes ==="
               )
          _ <-
            ZIO.debug(
              "Scenario: 5 concurrent requests with random success/failure patterns\n"
            )

          cb <- CircuitBreaker.make(maxFailures = 3, resetTimeout = 2.seconds)
          // Mix of outcomes: some succeed, some fail
          service =
            new TestService(
              scala.util.Random.shuffle(List(false, true, false, false, false))
            )

          stateBefore <- cb.currentState
          _           <- ZIO.debug(s"State before: $stateBefore")
          _           <- ZIO.debug("Sending 5 parallel requests...\n")

          results <-
            ZIO.foreachPar(1 to 5) { requestNum =>
              cb.protect(service.call)
                .as((requestNum, "success"))
                .tap(_ => ZIO.debug(s"[Request #$requestNum] ✓ Success"))
                .tapError(_ => ZIO.debug(s"[Request #$requestNum] ✗ Failed"))
                .catchAll {
                  case _: CircuitBreakerOpen =>
                    ZIO.debug(s"[Request #$requestNum] ⊗ Rejected") *>
                      ZIO.succeed((requestNum, "rejected"))
                  case _ =>
                    ZIO.succeed((requestNum, "failed"))
                }
            }

          stateAfter <- cb.currentState
          _          <- ZIO.debug(s"\nState after: $stateAfter")

          successCount  = results.count(_._2 == "success")
          rejectedCount = results.count(_._2 == "rejected")
          failedCount   = results.count(_._2 == "failed")

          _ <-
            ZIO.debug(
              s"\nResults: $successCount success, $failedCount failed, $rejectedCount rejected"
            )
        } yield ()

      // Example 4: Success resets counter
      def example4Reset: ZIO[Any, Nothing, Unit] =
        for {
          _ <- ZIO.debug("\n=== Example 4: Failure Counter Reset ===")
          _ <- ZIO.debug(
                 "Scenario: Successful request resets the failure counter\n"
               )
          cb <- CircuitBreaker.make(maxFailures = 3, resetTimeout = 1.second)
          service = new TestService(
                      List(
                        false, // Failure 1
                        false, // Failure 2
                        true,  // Success - resets counter to 0
                        false, // Failure 1 (counter reset)
                        false, // Failure 2
                        false  // Failure 3 - opens circuit
                      )
                    )

          _ <- ZIO.foreach(1 to 7) { i =>
                 for {
                   state <- cb.currentState
                   _     <- ZIO.debug(s"Request $i [Circuit: $state]")
                   _ <-
                     cb.protect(service.call)
                       .tap(_ =>
                         ZIO.debug(s"  ✓ Success (failure counter reset to 0)")
                       )
                       .tapError(_ => ZIO.debug(s"  ✗ Failed"))
                       .catchAll {
                         case _: CircuitBreakerOpen =>
                           ZIO.debug(s"  ⊗ Rejected - circuit is open")
                         case _ => ZIO.unit
                       }
                 } yield ()
               }

          finalState <- cb.currentState
          _          <- ZIO.debug(s"\nFinal state: $finalState")
          _ <-
            ZIO.debug(
              "Note: Request 3 succeeded and reset the counter, so it took 3 more"
            )
          _ <- ZIO.debug(
                 "      consecutive failures (requests 4-6) to open the circuit"
               )
        } yield ()

      def run =
        for {
          _ <- example1Basic
          _ <- example2aRecoverySuccess
          _ <- example2bRecoveryFailure
          _ <- example3Parallel.repeatN(5)
          _ <- example4Reset
          _ <- ZIO.debug("\n=== All examples completed ===")
        } yield ()
    }
  }

}
