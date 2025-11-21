package zionomicon.solutions

package ScopeComposableResources {

  /**
   *   1. Assume we have written a worker as follows:
   *
   * {{{
   * def worker(sem: Semaphore, id: Int): ZIO[Scope, Nothing, Unit] =
   *   for {
   *     _ <- sem.withPermitsScoped(2)
   *     _ <- Console.printLine(s"Request $id: Starting processing").orDie
   *     _ <- ZIO.sleep(5.seconds)
   *     _ <- Console.printLine(s"Request $id: Completed processing").orDie
   *   } yield ()
   * }}}
   *
   * Please explain how and why these two applications have different behavior:
   *
   * Application 1:
   *
   * {{{
   * object MainApp1 extends ZIOAppDefault {
   *   def run =
   *     for {
   *       sem <- Semaphore.make(4)
   *       _   <- ZIO.foreachParDiscard(1 to 10)(i => ZIO.scoped(worker(sem, i)))
   *     } yield ()
   * }
   * }}}
   *
   * Application 2:
   *
   * {{{
   * object MainApp2 extends ZIOAppDefault {
   *   def run =
   *     for {
   *       sem <- Semaphore.make(4)
   *       _   <- ZIO.scoped(ZIO.foreachParDiscard(1 to 10)(i => worker(sem, i)))
   *     } yield ()
   * }
   * }}}
   */
  package ComparingTwoWorkers {
    import zio._

    object Worker {
      def worker(sem: Semaphore, id: Int): ZIO[Scope, Nothing, Unit] =
        for {
          _ <- sem.withPermitsScoped(2)
          _ <- Console.printLine(s"Request $id: Starting processing").orDie
          _ <- ZIO.sleep(5.seconds)
          _ <- Console.printLine(s"Request $id: Completed processing").orDie
        } yield ()
    }

    // In Application 1, each worker invocation is wrapped in its own Scope
    // because of the ZIO.scoped call inside the foreachParDiscard. This means
    // that each worker will acquire and release its permits independently.
    // Since the semaphore has 4 permits and each worker requires 2 permits,
    // up to 2 workers can run concurrently. The remaining workers will wait
    // until permits are available.

    object MainApp1 extends ZIOAppDefault {
      import Worker._

      def run =
        for {
          sem <- Semaphore.make(4)
          _   <- ZIO.foreachParDiscard(1 to 10)(i => ZIO.scoped(worker(sem, i)))
        } yield ()
    }

    // In Application 2, there is a single Scope that encompasses all worker
    // invocations because of the outer ZIO.scoped call. This means that all
    // workers share the same lifetime for their acquired permits. As a result,
    // once the first 2 workers acquire their permits, they will hold onto
    // them until the entire scoped block completes. This blocks any other
    // workers from acquiring permits. They wait forever for permits that won't
    // be released which causes deadlock.

    object MainApp2 extends ZIOAppDefault {
      import Worker._

      def run =
        for {
          sem <- Semaphore.make(4)
          _   <- ZIO.scoped(ZIO.foreachParDiscard(1 to 10)(i => worker(sem, i)))
        } yield ()
    }
  }

  /**
   *   2. Continuing from implementing the `Semaphore` data type from the
   *      previous chapter, implement the `withPermits` operator, which takes
   *      the number of permits to acquire and release within the lifetime of
   *      the `Scope`:
   *
   * {{{
   * trait Semaphore {
   *   def withPermitsScoped(n: Long): ZIO[Scope, Nothing, Unit]
   * }
   * }}}
   */
  package SemaphoreWithPermitsScopedImpl {

    import zio._

    import scala.collection.immutable.Queue

    trait Semaphore {
      def withPermits[R, E, A](n: Long)(task: ZIO[R, E, A]): ZIO[R, E, A]

      /**
       * Acquires n permits and registers their release with the Scope. The
       * permits will be automatically released when the Scope closes.
       *
       * @param n
       *   the number of permits to acquire
       * @return
       *   an effect that requires Scope and completes when permits are acquired
       */
      def withPermitsScoped(n: Long): ZIO[Scope, Nothing, Unit]
    }

    object Semaphore {

      private case class State(
        permits: Long,
        waiting: Queue[(Long, Promise[Nothing, Unit])]
      )

      def make(permits: => Long): UIO[Semaphore] =
        Ref.make(State(permits, Queue.empty)).map { ref =>
          new Semaphore {

            private def acquire(n: Long): UIO[Unit] =
              ZIO.suspendSucceed {
                if (n <= 0) {
                  ZIO.unit
                } else {
                  Promise.make[Nothing, Unit].flatMap { promise =>
                    ref.modify { state =>
                      if (state.permits >= n) {
                        (
                          ZIO.unit,
                          state.copy(permits = state.permits - n)
                        )
                      } else {
                        (
                          promise.await,
                          state
                            .copy(waiting = state.waiting.enqueue((n, promise)))
                        )
                      }
                    }.flatten
                  }
                }
              }

            private def release(n: Long): UIO[Unit] = {
              def satisfyWaiters(state: State): (UIO[Unit], State) =
                state.waiting.dequeueOption match {
                  case Some(((needed, promise), rest))
                      if state.permits >= needed =>
                    val newState = state.copy(
                      permits = state.permits - needed,
                      waiting = rest
                    )
                    val (moreEffects, finalState) = satisfyWaiters(newState)
                    (promise.succeed(()) *> moreEffects, finalState)
                  case _ =>
                    (ZIO.unit, state)
                }

              ZIO.suspendSucceed {
                if (n <= 0) {
                  ZIO.unit
                } else {
                  ref.modify { state =>
                    val stateWithPermits =
                      state.copy(permits = state.permits + n)
                    satisfyWaiters(stateWithPermits)
                  }.flatten
                }
              }
            }

            def withPermits[R, E, A](
              n: Long
            )(task: ZIO[R, E, A]): ZIO[R, E, A] =
              ZIO.acquireReleaseWith(acquire(n))(_ => release(n))(_ => task)

            /**
             * Acquires permits and registers their release with the ambient
             * Scope. The permits are held until the Scope closes, at which
             * point they're automatically released.
             */
            def withPermitsScoped(n: Long): ZIO[Scope, Nothing, Unit] =
              ZIO.acquireRelease(acquire(n))(_ => release(n))
          }
        }
    }
  }

}
