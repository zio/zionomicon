package zionomicon.exercises

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
  package ComparingTwoWorkers {}

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
  package SemaphoreWithPermitsScopedImpl {}
}
