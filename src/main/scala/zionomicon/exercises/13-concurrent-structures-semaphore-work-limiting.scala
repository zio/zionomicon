package zionomicon.exercises

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
  package DynamicSemaphoreImpl {}

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
  package DiningPhilosophersImpl {}
}
