package zionomicon.exercises

package StmComposingAtomicity {

  /**
   *   1. Create a concurrent counter using `TRef` and STM. Implement increment
   *      and decrement operations and ensure thread safety when multiple
   *      transactions modify the counter concurrently.
   */
  package ConcurrentCounterImpl {}

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
  package CountdownLatchImpl {}

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
