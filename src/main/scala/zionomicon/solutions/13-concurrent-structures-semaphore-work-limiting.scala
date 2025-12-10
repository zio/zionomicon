package zionomicon.solutions

package SemaphoreWorkLimiting {

  /**
   *   1. Implement a semaphore where the number of available permits can be
   *      adjusted dynamically at runtime. This is useful for systems that need
   *      to adapt their concurrency based on load or system resources.
   *
   * {{{
   *   trait DynamicSemaphore {
   *     def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
   *     def updatePermits(delta: Int): UIO[Unit]
   *     def currentPermits: UIO[Int]
   *   }
   * }}}
   *
   * Challenge: Ensure that reducing permits doesn't affect already-running
   * tasks, only future acquisitions.
   *
   * Hint: Please note that implementing the withPermit method will require
   * careful handling of resource acquisition and release to ensure that the
   * acquired permit is properly released after the task completes, regardless
   * of whether it succeeds, fails, or is interrupted. Consider using ZIO's
   * `ZIO.acquireRelease*` to manage this lifecycle effectively, which will be
   * discussed in the next chapter.
   */
  package DynamicSemaphoreImpl {

    import zio._

    import scala.collection.immutable.Queue

    trait DynamicSemaphore {
      def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]

      def updatePermits(delta: Int): UIO[Unit]

      def currentPermits: UIO[Int]
    }

    object DynamicSemaphore {
      def make(initialPermits: Int): UIO[DynamicSemaphore] =
        for {
          state <-
            Ref.make((initialPermits, 0, Queue.empty[Promise[Nothing, Unit]]))
          // (maxPermits, activeCount, waitQueue)
        } yield new DynamicSemaphore {
          def withPermit[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] = {
            def acquire: UIO[Unit] =
              for {
                promise <- Promise.make[Nothing, Unit]
                shouldWait <- state.modify { case (max, active, queue) =>
                                if (active < max)
                                  (false, (max, active + 1, queue))
                                else
                                  (true, (max, active, queue.enqueue(promise)))
                              }
                _ <- if (shouldWait) promise.await else ZIO.unit
              } yield ()

            def release: UIO[Unit] = {
              state.modify { case (max, active, queue) =>
                queue.dequeueOption match {
                  case Some((head, tail)) =>
                    // Wake up next waiter, keep active count same
                    (head.succeed(()), (max, active, tail))
                  case None =>
                    // No waiters, decrement active
                    (ZIO.unit, (max, active - 1, Queue.empty))
                }
              }.flatten
            }.unit

            ZIO.acquireReleaseWith(acquire)(_ => release)(_ => zio)
          }

          def updatePermits(delta: Int): UIO[Unit] =
            ZIO.uninterruptible {
              state.modify { case (max, active, queue) =>
                val newMax = max + delta
                if (delta > 0) {
                  // Increase permits - update max and wake up waiters
                  val toWake      = queue.take(delta.min(queue.size))
                  val remaining   = queue.drop(delta.min(queue.size))
                  val wakeEffects = ZIO.foreachDiscard(toWake)(_.succeed(()))
                  (wakeEffects, (newMax, active + toWake.size, remaining))
                } else {
                  // Decrease permits - just update max
                  (ZIO.unit, (newMax, active, queue))
                }
              }.flatten
            }

          def currentPermits: UIO[Int] = state.get.map(_._1)
        }
    }

    object DynamicSemaphoreExample extends ZIOAppDefault {

      def task(id: Int, duration: Duration): ZIO[Any, Nothing, Unit] =
        for {
          _ <- Console.printLine(s"Task $id: Started").orDie
          _ <- ZIO.sleep(duration)
          _ <- Console.printLine(s"Task $id: Completed").orDie
        } yield ()

      def run =
        for {
          _ <- Console.printLine("=== Dynamic Semaphore Example ===\n").orDie

          // Create a semaphore with 2 initial permits
          semaphore <- DynamicSemaphore.make(2)

          _ <- Console
                 .printLine(s"Initial permits: 2")
                 .orDie
          _ <- semaphore.currentPermits
                 .debug("Current Permits")
                 .delay(1.seconds)
                 .forever
                 .forkDaemon

          _ <- Console
                 .printLine("Example 1: Start running 50 tasks in background")
                 .orDie
          fiber <- ZIO
                     .foreachPar((1 to 20).toList) { i =>
                       semaphore.withPermit(task(i, 1.second))
                     }
                     .fork
          _ <- Console.printLine("\n").orDie

          _ <- ZIO.sleep(2.seconds)
          _ <- Console
                 .printLine("\n>>> Increasing permits from 2 to 4 <<<\n")
                 .orDie
          _ <- semaphore.updatePermits(2) // Add 2 more permits

          _ <- fiber.join

          _ <- Console
                 .printLine("\n>>> Decreasing permits from 4 to 3 <<<\n")
                 .orDie
          _ <- semaphore.updatePermits(-1) // Remove 2 permits
          _ <- ZIO
                 .foreachPar((21 to 40).toList) { i =>
                   semaphore.withPermit(task(i, 1.second))
                 }
        } yield ()

    }
  }

  /**
   * Solve the classic dining philosophers problem using Semaphores to prevent
   * deadlock. Five philosophers sit at a round table with five forks. Each
   * philosopher needs two adjacent forks to eat.
   *
   * {{{
   *  trait DiningPhilosophers {
   *    def philosopherLifecycle(id: Int): ZIO[Any, Nothing, Unit]
   *    def runDinner(duration: Duration): ZIO[Any, Nothing, Map[Int, Int]] //
   * philosopher -> meals eaten
   *  }
   * }}}
   *
   * Must prevent both deadlock and starvation.
   */
  package DiningPhilosophersImpl {

    import zio._

    trait DiningPhilosophers {
      def philosopherLifecycle(id: Int): ZIO[Any, Nothing, Unit]

      def runDinner(duration: Duration): ZIO[Any, Nothing, Map[Int, Int]]
    }

    object DiningPhilosophers {
      def make: UIO[DiningPhilosophers] =
        for {
          // 5 forks represented as individual semaphores
          forks <- ZIO.foreach((0 until 5).toList)(_ => Semaphore.make(1))

          // Limit concurrent dining to prevent deadlock
          diningRoom <- Semaphore.make(4) // Max 4 philosophers can try to eat

          // Track meals eaten
          mealsEaten <- Ref.make(Map.empty[Int, Int].withDefaultValue(0))

        } yield new DiningPhilosophers {

          private def randomSleep: UIO[Unit] =
            Random.nextIntBounded(100).flatMap(n => ZIO.sleep(n.millis))

          def philosopherLifecycle(id: Int): ZIO[Any, Nothing, Unit] = {
            val leftFork  = forks(id)
            val rightFork = forks((id + 1) % 5)

            def think: UIO[Unit] =
              for {
                _ <- Console.printLine(s"Philosopher $id is thinking").orDie
                _ <- randomSleep
              } yield ()

            def eat: UIO[Unit] =
              diningRoom.withPermit {
                leftFork.withPermit {
                  rightFork.withPermit {
                    for {
                      _ <-
                        Console.printLine(s"Philosopher $id is eating").orDie
                      _ <- randomSleep
                      _ <- mealsEaten.update(m => m + (id -> (m(id) + 1)))
                    } yield ()
                  }
                }
              }

            (think *> eat).forever
          }

          def runDinner(
            duration: Duration
          ): ZIO[Any, Nothing, Map[Int, Int]] =
            for {
              fibers <- ZIO.foreachParDiscard((0 until 5).toList)(id =>
                          philosopherLifecycle(id)
                        ).timeout(duration).unit
              meals <- mealsEaten.get
            } yield meals
        }
    }

    object DiningPhilosophersExample extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        for {
          // Create the dining philosophers instance
          philosophers <- DiningPhilosophers.make

          // Example 1: Run for 5 seconds and show results
          _      <- Console.printLine("=== Starting 5-second dinner ===")
          meals1 <- philosophers.runDinner(5.seconds)
          _      <- printResults(meals1)

          // Example 2: Compare fairness across multiple runs
          _ <- Console.printLine(
                 "\n=== Testing fairness (3 runs of 5 seconds) ==="
               )
          _ <- ZIO.foreachDiscard(1 to 3) { runNum =>
                 for {
                   philosophers <- DiningPhilosophers.make
                   _            <- Console.printLine(s"\nRun $runNum:")
                   meals        <- philosophers.runDinner(5.seconds)
                   _            <- printResults(meals)
                 } yield ()
               }

        } yield ()

      def printResults(meals: Map[Int, Int]): UIO[Unit] = {
        for {
          _ <- Console.printLine("\nMeal counts:")
          _ <- ZIO.foreachDiscard(0 until 5) { id =>
                 Console.printLine(s"  Philosopher $id: ${meals(id)} meals")
               }
          total = meals.values.sum
          avg   = total / 5.0
          _    <- Console.printLine(s"  Total: $total meals")
          _    <- Console.printLine(f"  Average: $avg%.1f meals per philosopher")

          // Show fairness metric
          maxMeals = meals.values.max
          minMeals = meals.values.min
          fairness =
            if (maxMeals == 0) 100.0 else (minMeals.toDouble / maxMeals * 100)
          _ <-
            Console.printLine(f"  Fairness: $fairness%.1f%% (min/max ratio)")
        } yield ()
      }.orDie
    }
  }

}
