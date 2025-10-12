package zionomicon.exercises

package PromiseWorkSynchronization {

  /**
   *   1. Implement a countdown latch using `Ref` and `Promise`. A countdown
   *      latch is a synchronization aid that allows one or more threads to wait
   *      until a set of operations being performed in other threads completes.
   *      The latch is initialized with a given count, and the count is
   *      decremented each time an operation completes. When the count reaches
   *      zero, all waiting threads are released:
   *
   * {{{
   *     trait CountDownLatch {
   *       def countDown: UIO[Unit]
   *       def await: UIO[Unit]
   *     }
   *
   *     object CountDownLatch {
   *       def make(n: Int): UIO[CountDownLatch] = ???
   *     }
   * }}}
   */

  package CountDownLatchImpl {}

  /**
   *   2. Similar to the previous exercise, you can implement `CyclicBarrier`. A
   *      cyclic barrier is a synchronization aid that allows a set of threads
   *      to all wait for each other to reach a common barrier point. Once all
   *      threads have reached the barrier, they can proceed:
   *
   * {{{
   *     trait CyclicBarrier {
   *       def await: UIO[Unit]
   *       def reset: UIO[Unit]
   *     }
   *
   *     object CyclicBarrier {
   *       def make(parties: Int): UIO[CyclicBarrier] = ???
   *     }
   * }}}
   */
  package CyclicBarrierImpl {}

  /**
   *   3. Implement a concurrent bounded queue using `Ref` and `Promise`. It
   *      should support enqueueing and dequeueing operations, blocking when the
   *      queue is full or empty:
   *
   * {{{
   *     trait Queue[A] {
   *       def offer(a: A): UIO[Unit]
   *       def take: UIO[A]
   *     }
   *
   *     object Queue {
   *       def make[A](capacity: Int): UIO[Queue[A]] = ???
   *     }
   * }}}
   */

  package BoundedQueueImpl {}
}
