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
   *
   * Hint: Use a sliding queue to store the results of the most recent
   * operations and track the number of failures.
   */
  package CircuitBreakerImpl {

    package A {

      import zio._
      import zionomicon.solutions.QueueWorkDistribution.CircuitBreakerImpl.A.CircuitBreaker.CircuitBreakerOpen

      /** Simple Circuit Breaker implementation */
      trait CircuitBreaker {
        def protect[A](operation: Task[A]): Task[A]
        def currentState: UIO[CircuitBreaker.State]
      }

      object CircuitBreaker {
        sealed trait State
        object State {
          case object Closed   extends State
          case object HalfOpen extends State
          case object Open     extends State
        }

        case class CircuitBreakerOpen()
            extends Exception("Circuit breaker is open")

        def make(
          maxFailures: Int,
          resetTimeout: Duration
        ): ZIO[Any, Nothing, CircuitBreaker] =
          for {
            state          <- Ref.make[State](State.Closed)
            failureCount   <- Ref.make(0)
            halfOpenSwitch <- Ref.make(true)
            resetFiber     <- Ref.make[Option[Fiber[Nothing, Unit]]](None)
            semaphore      <- Semaphore.make(1) // Serialize state updates
            cb = new CircuitBreakerImpl(
                   state,
                   failureCount,
                   halfOpenSwitch,
                   resetFiber,
                   semaphore,
                   maxFailures,
                   resetTimeout
                 )
          } yield cb

        private class CircuitBreakerImpl(
          state: Ref[State],
          failureCount: Ref[Int],
          halfOpenSwitch: Ref[Boolean],
          resetFiber: Ref[Option[Fiber[Nothing, Unit]]],
          semaphore: Semaphore,
          maxFailures: Int,
          resetTimeout: Duration
        ) extends CircuitBreaker {

          override def currentState: UIO[State] = state.get

          private def scheduleReset: UIO[Unit] =
            for {
              // Cancel any existing reset
              existingFiber <- resetFiber.get
              _             <- ZIO.foreachDiscard(existingFiber)(_.interrupt)
              // Schedule new reset
              fiber <-
                (ZIO.sleep(resetTimeout) *> transitionToHalfOpen).forkDaemon
              _ <- resetFiber.set(Some(fiber))
            } yield ()

          private def transitionToHalfOpen: UIO[Unit] =
            for {
              _ <- halfOpenSwitch.set(true)
              _ <- state.set(State.HalfOpen)
            } yield ()

          private def transitionToOpen: UIO[Unit] =
            for {
              _ <- state.set(State.Open)
              _ <- scheduleReset
            } yield ()

          private def transitionToClosed: UIO[Unit] =
            for {
              _ <- failureCount.set(0)
              _ <- state.set(State.Closed)
              // Cancel any pending reset
              fiber <- resetFiber.get
              _     <- ZIO.foreachDiscard(fiber)(_.interrupt)
              _     <- resetFiber.set(None)
            } yield ()

          override def protect[A](operation: Task[A]): Task[A] =
            state.get.flatMap {
              case State.Closed =>
                operation.tapBoth(
                  error => onFailure,
                  _ => onSuccess
                )

              case State.Open =>
                ZIO.fail(CircuitBreakerOpen())

              case State.HalfOpen =>
                // Use uninterruptibleMask to ensure state transitions happen atomically
                ZIO.uninterruptibleMask { restore =>
                  for {
                    // Only one request gets through in half-open state
                    isFirstCall <- halfOpenSwitch.getAndSet(false)
                    _           <- ZIO.fail(CircuitBreakerOpen()).unless(isFirstCall)
                    result <- restore(operation)
                                .onInterrupt(
                                  halfOpenSwitch.set(true)
                                ) // Reset if interrupted
                                .tapBoth(
                                  _ => transitionToOpen,
                                  _ => transitionToClosed
                                )
                  } yield result
                }
            }

          private def onFailure: UIO[Unit] =
            semaphore.withPermit {
              for {
                currentState <- state.get
                _ <- (
                       failureCount
                         .updateAndGet(_ + 1)
                         .flatMap { count =>
                           // Re-check state to avoid race condition
                           state.get.flatMap {
                             case State.Closed if count >= maxFailures =>
                               transitionToOpen
                             case _ =>
                               ZIO.unit
                           }
                         }
                       )
                       .when(currentState == State.Closed)
                       .uninterruptible
              } yield ()
            }

          private def onSuccess: UIO[Unit] =
            semaphore.withPermit {
              for {
                currentState <- state.get
                _            <- failureCount.set(0).when(currentState == State.Closed)
              } yield ()
            }
        }
      }

      // ============================================================================
      // EXAMPLE USAGE
      // ============================================================================

      object CircuitBreakerExample extends ZIOAppDefault {

        sealed trait RequestOutcome
        object RequestOutcome {
          case object Success extends RequestOutcome
          case object Failure extends RequestOutcome
        }

        // Service that responds based on predetermined outcomes
        class TestService(outcomes: Ref[List[RequestOutcome]]) {
          def call: Task[String] =
            outcomes.modify {
              case head :: tail => (head, tail)
              case Nil =>
                (
                  RequestOutcome.Success,
                  Nil
                ) // Default to success if list exhausted
            }.flatMap {
              case RequestOutcome.Success => ZIO.succeed("Success")
              case RequestOutcome.Failure =>
                ZIO.fail(new Exception("Service failure"))
            }
        }

        def run =
          ZIO.scoped {
            for {

              _ <- ZIO.foreach(1 to 500) { iteration =>
                     for {
                       cb <- CircuitBreaker.make(
                               maxFailures = 3,
                               resetTimeout = 2000.millis
                             )
//                      1. Create a list of success and failure outcomes and shuffle it
                       outcomes <- Random
                                     .shuffle(
                                       List.fill(2)(
                                         RequestOutcome.Success
                                       ) ++ List.fill(3)(RequestOutcome.Failure)
                                     )
                                     .map(_.toList: List[RequestOutcome])
//                     outcomes: List[RequestOutcome] = List(
//                                                        RequestOutcome.Success,
//                                                        RequestOutcome.Success,
//                                                        RequestOutcome.Failure,
//                                                        RequestOutcome.Failure,
//                                                        RequestOutcome.Failure
//                                                      )
                       outcomeRef <- Ref.make(outcomes)
                       service     = new TestService(outcomeRef)

                       // 2. Debug the state of the circuit breaker before requests
                       stateBefore <- cb.currentState
                       _ <-
                         ZIO.debug(
                           s"[Iteration $iteration] State before: $stateBefore"
                         )

                       // 3. Run foreachPar and execute requests against the service
                       results <-
                         ZIO.foreachPar(1 to 5) { requestNum =>
                           cb.protect(service.call)
                             .as("success")
                             .tapBoth(
                               _ => ZIO.unit,
                               _ =>
                                 ZIO.debug(
                                   s"[Iteration $iteration] Request #$requestNum: success"
                                 )
                             )
                             .catchAll {
                               case _: CircuitBreakerOpen =>
                                 ZIO.debug(
                                   s"[Iteration $iteration] Request #$requestNum: rejected"
                                 ) *>
                                   ZIO.succeed("rejected")
                               case e =>
                                 ZIO.debug(
                                   s"[Iteration $iteration] Request #$requestNum: failed"
                                 ) *>
                                   ZIO.succeed("failed")
                             }
                         }

                       // 4. Debug the state of the circuit breaker after requests
                       stateAfter <- cb.currentState
                       _ <-
                         ZIO.debug(
                           s"[Iteration $iteration] State after:  $stateAfter"
                         )

                       // Count outcomes for this iteration
                       successCount  = results.count(_ == "success")
                       rejectedCount = results.count(_ == "rejected")
                       failedCount   = results.count(_ == "failed")
                       _ <-
                         ZIO.debug(
                           s"[Iteration $iteration] Results: $successCount success, $failedCount failed, $rejectedCount rejected\n"
                         )

                       // Small delay between iterations to allow circuit breaker to potentially reset
                       _ <- ZIO.sleep(60.millis)
                     } yield ()
                   }

              _ <- ZIO.debug("=== Test completed ===")
            } yield ()
          }.exitCode
      }
    }

  }

}
