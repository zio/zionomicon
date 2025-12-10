package zionomicon.solutions

package StmComposingAtomicity {

  /**
   * Solve the Dining Philosophers problem from Chapter 13 again, this time
   * using ZIO STM instead. Compare your solution with your previous
   * Semaphore-based implementation.
   */
  package DiningPhilosophers {
    import zio._
    import zio.stm._

    final case class Fork private (available: TRef[Boolean]) {
      def acquire: STM[Nothing, Unit] =
        for {
          isAvailable <- available.get
          _           <- if (isAvailable) available.set(false) else STM.retry
        } yield ()

      def release: STM[Nothing, Unit] =
        available.set(true)
    }

    object Fork {
      def make: UIO[Fork] =
        TRef.make(true).commit.map(Fork(_))
    }

    trait DiningPhilosophers {
      def philosopherLifecycle(id: Int): ZIO[Any, Nothing, Unit]
      def runDinner(duration: Duration): ZIO[Any, Nothing, Map[Int, Int]]
    }

    object DiningPhilosophers {
      def make: UIO[DiningPhilosophers] =
        for {
          forks      <- ZIO.collectAll(ZIO.replicate(5)(Fork.make)).map(_.toList)
          mealsEaten <- Ref.make(Map.empty[Int, Int].withDefaultValue(0))
        } yield new DiningPhilosophers {
          override def philosopherLifecycle(
            id: Int
          ): ZIO[Any, Nothing, Unit] = {
            val left  = forks(id)
            val right = forks((id + 1) % 5)
            (think(id) *> eat(id, left, right)).forever
          }

          override def runDinner(
            duration: Duration
          ): ZIO[Any, Nothing, Map[Int, Int]] =
            for {
              // Reset meal counts for fresh run
              _ <- mealsEaten.set((0 until 5).map(_ -> 0).toMap)

              // Run all philosophers concurrently
              philosophers = (0 until 5).map(philosopherLifecycle)

              // Let them run for the specified duration, then interrupt
              _ <- ZIO
                     .collectAllParDiscard(philosophers)
                     .timeout(duration)
                     .unit

              meals <- mealsEaten.get
            } yield meals

          private def randomSleep: UIO[Unit] =
            Random.nextIntBounded(100).flatMap(n => ZIO.sleep(n.millis))

          private def think(id: Int): UIO[Unit] =
            for {
              _ <- Console.printLine(s"Philosopher $id is thinking").orDie
              _ <- randomSleep
            } yield ()

          private def eat(id: Int, left: Fork, right: Fork): UIO[Unit] =
            ZIO.scoped {
              for {
                _ <- ZIO.acquireRelease(
                       (left.acquire *> right.acquire).commit
                     )(_ => (left.release *> right.release).commit)
                _ <- Console.printLine(s"Philosopher $id is eating 🍝").orDie
                _ <- randomSleep
                _ <- mealsEaten.update(m => m.updated(id, m(id) + 1))
              } yield ()
            }
        }
    }

    object DiningPhilosophersExample extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        for {
          philosophers <- DiningPhilosophers.make

          _      <- Console.printLine("=== Starting 5-second dinner ===")
          meals1 <- philosophers.runDinner(5.seconds)
          _      <- printResults(meals1)

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

          maxMeals = meals.values.max
          minMeals = meals.values.min
          fairness =
            if (maxMeals == 0) 100.0 else (minMeals.toDouble / maxMeals * 100)
          _ <- Console.printLine(f"  Fairness: $fairness%.1f%% (min/max ratio)")
        } yield ()
      }.orDie
    }
  }

  /**
   *   2. Implement a simple countdown latch using ZIO STM's `TRef`. A countdown
   *      latch starts with a specified count (`n`). It provides two primary
   *      operations:
   *   - **`countDown`**: Decrements the count by one but does nothing if it is
   *     already zero.
   *   - **`await`**: Suspends the calling fiber until the count reaches zero,
   *     allowing it to proceed only after all countdowns have been completed.
   *
   * Note: This exercise is for educational purposes to help you understand the
   * basics of STM. ZIO already provides a `CountDownLatch` implementation with
   * more basic concurrency primitives.
   */
  package CountdownLatchImpl {
    import zio._
    import zio.stm._

    /**
     * A countdown latch implementation using ZIO STM.
     *
     * The latch starts with a count `n` and provides two operations:
     *   - countDown: Atomically decrements the count (or does nothing if
     *     already zero)
     *   - await: Suspends the calling fiber until the count reaches zero
     *
     * This demonstrates the power of STM for synchronization: the await
     * operation can simply express the condition it's waiting for (count == 0)
     * and STM will automatically handle retry logic when the count changes.
     *
     * Both operations return UIO effects, hiding the STM implementation details
     * and providing a simple, composable interface for users.
     */
    trait CountDownLatch {
      def countDown: UIO[Unit]
      def await: UIO[Unit]
    }

    object CountDownLatch {
      def make(n: Int): UIO[CountDownLatch] =
        TRef.make(Math.max(n, 0)).commit.map { count =>
          new CountDownLatch {

            /**
             * Atomically decrements the count by one. Does nothing if the count
             * is already zero.
             */
            def countDown: UIO[Unit] =
              count.update { current =>
                if (current > 0) current - 1 else 0
              }.commit

            /**
             * Suspends the calling fiber until the count reaches zero.
             *
             * Uses STM.retry to automatically wait for changes to the count.
             * The transaction will only re-execute when the underlying TRef
             * changes, avoiding busy-waiting.
             */
            def await: UIO[Unit] =
              count.get.flatMap { current =>
                if (current == 0)
                  STM.unit
                else
                  STM.retry
              }.commit
          }
        }
    }

    object CountDownLatchExamples extends ZIOAppDefault {
      val examples = List(
        ("Basic Countdown", example1Basic),
        ("Concurrent Fibers", example2Concurrent),
        ("Multiple Waiters", example3MultipleWaiters),
        ("Batch Processor", example4BatchProcessor)
      )

      def run =
        ZIO.foreachDiscard(examples) { case (name, example) =>
          ZIO.succeed(println(s"\n=== $name ===")) *> example
        }

      /**
       * Example 1: Basic countdown - single fiber increments count and awaits
       */
      def example1Basic: ZIO[Any, Nothing, Unit] = {
        val program = for {
          latch <- CountDownLatch.make(3)

          // Simulate three fibers completing work
          _ <- ZIO
                 .foreachDiscard(1 to 3) { i =>
                   ZIO.succeed(println(s"Work $i starting")) *>
                     ZIO.sleep(100.millis) *>
                     ZIO.succeed(println(s"Work $i completed")) *>
                     latch.countDown
                 }
                 .fork

          // Main fiber waits for all work to complete
          _ <- ZIO.succeed(println("Waiting for all work to complete..."))
          // This will suspend until count reaches zero (comment this line to see the difference)
          _ <- latch.await
          _ <- ZIO.succeed(println("All work completed!"))
        } yield ()

        program
      }

      /**
       * Example 2: Concurrent fibers with parallel work
       *
       * This demonstrates that the latch correctly handles multiple fibers
       * working concurrently and calling countDown atomically.
       */
      def example2Concurrent: ZIO[Any, Nothing, Unit] = {
        val program = for {
          latch <- CountDownLatch.make(5)

          // 5 worker fibers that do work and signal completion
          workers = (1 to 5).map { workerId =>
                      ZIO.sleep((Math.random() * 200).toLong.millis) *>
                        ZIO.succeed(println(s"Worker $workerId completing")) *>
                        latch.countDown
                    }

          // Waiter fiber that waits for all workers
          waiter = for {
                     _ <- ZIO.debug("Waiter: starting to wait for workers...")
                     _ <- latch.await
                     _ <- ZIO.debug("Waiter: all workers completed!")
                   } yield ()

          // Run workers in parallel and waiter concurrently
          _ <- ZIO.collectAllParDiscard(workers) <&> waiter
        } yield ()

        program
      }

      /**
       * Example 3: Multiple waiters
       *
       * Demonstrates that multiple fibers can await the same latch and will all
       * be released when the count reaches zero.
       */
      def example3MultipleWaiters: ZIO[Any, Nothing, Unit] = {
        val program = for {
          latch <- CountDownLatch.make(3)

          // Multiple waiters
          waiter = (waiterId: Int) =>
                     for {
                       _ <- ZIO.debug(s"Waiter $waiterId: waiting")
                       _ <- latch.await
                       _ <- ZIO.debug(s"Waiter $waiterId: released")
                     } yield ()

          // Workers
          worker = (workerId: Int) =>
                     for {
                       _ <- ZIO.sleep((Math.random() * 200).toLong.millis)
                       _ <- ZIO.debug(s"Worker $workerId: completing")
                       _ <- latch.countDown
                     } yield ()

          // Run 3 workers and 4 waiters in parallel
          _ <- ZIO.collectAllParDiscard(
                 (1 to 3).map(worker) ++ (1 to 4).map(waiter)
               )
        } yield ()

        program
      }

      /**
       * Example 4: Real-world pattern - batch work processor
       *
       * Demonstrates using a countdown latch to implement a pattern where a
       * supervisor waits for a batch of workers to complete before proceeding
       * with the next batch.
       */
      def example4BatchProcessor: ZIO[Any, Nothing, Unit] = {
        def processBatch(
          batchId: Int,
          itemCount: Int
        ): ZIO[Any, Nothing, Unit] =
          for {
            latch <- CountDownLatch.make(itemCount)

            // Process items in parallel
            _ <- ZIO.debug(s"Batch $batchId: starting $itemCount items")

            items =
              (1 to itemCount).map { itemId =>
                ZIO.sleep((Math.random() * 100).toLong.millis) *>
                  ZIO.debug(s"Batch $batchId: item $itemId done") *>
                  latch.countDown
              }

            _ <- ZIO.collectAllParDiscard(items)
            _ <- latch.await

            _ <- ZIO.debug(s"Batch $batchId: all items complete")
          } yield ()

        for {
          _ <- processBatch(1, 3)
          _ <- processBatch(2, 4)
          _ <- processBatch(3, 2)
        } yield ()
      }
    }
  }

  /**
   *   3. Implement a Read-writer Lock using STM. A read-writer lock allows
   *      multiple readers to access a resource concurrently but requires
   *      exclusive access for writers. Implement the following operations:
   *      \index{Reader-writer Lock}
   *
   * {{{
   * trait ReadWriteLock {
   *   def readWith[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
   *   def writeWith[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
   * }
   * }}}
   */
  package ReadWriteLockImpl {}
}
