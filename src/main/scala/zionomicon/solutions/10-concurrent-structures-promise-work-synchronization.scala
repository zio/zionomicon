package zionomicon.solutions

package PromiseWorkSynchronization {

  /**
   *   1. Implement a countdown latch using `Ref` and `Promise`. A countdown
   *      latch is a synchronization aid that allows one or more threads to wait
   *      until a set of operations being performed in other threads completes.
   *      The latch is initialized with a given count, and the count is
   *      decremented each time an operation completes. When the count reaches
   *      zero, all waiting threads are released:
   *
   *     ```scala mdoc:invisible
   *     trait CountDownLatch {
   *       def countDown: UIO[Unit]
   *       def await: UIO[Unit]
   *     }
   *
   *     object CountDownLatch {
   *       def make(n: Int): UIO[CountDownLatch] = ???
   *     }
   *     ```
   */

  package CountDownLatchImpl {
    import zio._

    final case class CountDownLatch(
      count: Ref[Int],
      promise: Promise[Nothing, Unit]
    ) {
      def countDown: UIO[Unit] =
        count.modify { current =>
          if (current <= 0) {
            (ZIO.unit, 0)
          } else {
            val newCount = current - 1
            val effect =
              if (newCount == 0)
                promise.succeed(()).unit
              else
                ZIO.unit
            (effect, newCount)
          }
        }.flatten

      def await: UIO[Unit] =
        promise.await

      def getCount: UIO[Int] =
        count.get
    }

    object CountDownLatch {
      def make(n: Int): UIO[CountDownLatch] =
        if (n <= 0)
          ZIO.die(new IllegalArgumentException("n must be positive"))
        else
          Ref.make(n).zipWith(Promise.make[Nothing, Unit])(CountDownLatch(_, _))
    }

    object CountDownLatchExample extends ZIOAppDefault {
      def run =
        for {
          latch <- CountDownLatch.make(3)

          // Start 3 fibers that will count down
          _ <- ZIO
                 .foreachPar(1 to 3) { i =>
                   for {
                     _ <- ZIO.debug(s"Fiber $i starting work...")
                     _ <- ZIO.sleep(i.seconds)
                     _ <- ZIO.debug(s"Fiber $i completed!")
                     _ <- latch.countDown
                   } yield ()
                 }
                 .fork

          // Main fiber waits for all to complete
          _ <- ZIO.debug("Waiting for all fibers to complete...")
          _ <- latch.await
          _ <- ZIO.debug("All fibers completed!")
        } yield ()
    }

  }

  /**
   *   2. Similar to the previous exercise, you can implement `CyclicBarrier`. A
   *      cyclic barrier is a synchronization aid that allows a set of threads
   *      to all wait for each other to reach a common barrier point. Once all
   *      threads have reached the barrier, they can proceed:
   *
   *     ```scala mdoc:invisible
   *     trait CyclicBarrier {
   *       def await: UIO[Unit]
   *       def reset: UIO[Unit]
   *     }
   *
   *     object CyclicBarrier {
   *       def make(parties: Int): UIO[CyclicBarrier] = ???
   *     }
   * ```
   */
  package CyclicBarrierImpl {

    import zio._

    // Please note that this is an educational implementation and may not
    // be suitable for production use. If you want a well-tested and robust
    // implementation, consider using the `zio.concurrent.CyclicBarrier`
    // provided by ZIO.
    final case class CyclicBarrier(
      parties: Int,
      waiting: Ref[Int],
      promise: Ref[Promise[Nothing, Unit]]
    ) {
      def await: UIO[Unit] =
        for {
          currentPromise <- promise.get
          shouldRelease <- waiting.modify { current =>
                             val newWaiting = current + 1
                             if (newWaiting == parties) {
                               // Last thread to arrive - release everyone and reset
                               (true, 0)
                             } else {
                               // Not the last thread - keep waiting
                               (false, newWaiting)
                             }
                           }
          _ <- if (shouldRelease) {
                 // Complete the current promise to release all waiting threads
                 currentPromise.succeed(()).unit *>
                   // Create a new promise for the next cycle
                   Promise
                     .make[Nothing, Unit]
                     .flatMap(newPromise => promise.set(newPromise))
               } else {
                 // Wait for all threads to arrive
                 currentPromise.await
               }
        } yield ()

      def reset: UIO[Unit] =
        for {
          _          <- waiting.set(0)
          newPromise <- Promise.make[Nothing, Unit]
          _          <- promise.set(newPromise)
        } yield ()
    }

    object CyclicBarrier {
      def make(parties: Int): UIO[CyclicBarrier] =
        if (parties <= 0)
          ZIO.die(new IllegalArgumentException("parties must be positive"))
        else
          for {
            waiting        <- Ref.make(0)
            initialPromise <- Promise.make[Nothing, Unit]
            promiseRef     <- Ref.make(initialPromise)
          } yield CyclicBarrier(parties, waiting, promiseRef)
    }

    object CyclicBarrierExample extends ZIOAppDefault {
      def run =
        for {
          barrier <- CyclicBarrier.make(3)
          _ <- ZIO.foreachPar(1 to 3) { i =>
                 for {
                   _ <- ZIO.debug(s"Job $i: Starting work...")
                   _ <- ZIO.sleep(i.seconds)
                   _ <- ZIO.debug(s"Job $i: Reaching barrier...")
                   _ <- barrier.await
                   _ <- ZIO.debug(s"Job $i: Passed barrier!")
                 } yield ()
               }
        } yield ()
    }

  }

  /**
   *   3. Implement a concurrent bounded queue using `Ref` and `Promise`. It
   *      should support enqueueing and dequeueing operations, blocking when the
   *      queue is full or empty:
   *
   *     ```scala
   *     trait Queue[A] {
   *       def offer(a: A): UIO[Unit]
   *       def take: UIO[A]
   *     }
   *
   *     object Queue {
   *       def make[A](capacity: Int): UIO[Queue[A]] = ???
   *     }
   *     ```
   */

  // Please note that this is an educational implementation and may not be
  // suitable for production use. If you want a well-tested and robust
  // implementation, consider using the `zio.Queue.bounded` provided by ZIO.
  package BoundedQueueExercise {
    import zio._

    final case class BoundedQueue[A] private (
      capacity: Int,
      state: Ref[BoundedQueue.State[A]]
    ) extends Queue[A] {
      import BoundedQueue._

      def offer(a: A): UIO[Unit] =
        Promise.make[Nothing, Unit].flatMap { promise =>
          state.modify {
            case State(queue, waitingConsumers, waitingProducers) =>
              if (waitingConsumers.nonEmpty) {
                // There are waiting consumers, give the item directly to the first one
                val (consumer, remainingConsumers) =
                  (waitingConsumers.head, waitingConsumers.tail)
                val effect = consumer.succeed(a).unit
                (effect, State(queue, remainingConsumers, waitingProducers))
              } else if (queue.size < capacity) {
                // Queue has space, add the item
                (
                  ZIO.unit,
                  State(queue.enqueue(a), waitingConsumers, waitingProducers)
                )
              } else {
                // Queue is full, producer must wait
                (
                  promise.await,
                  State(
                    queue,
                    waitingConsumers,
                    waitingProducers.enqueue((a, promise))
                  )
                )
              }
          }.flatten
        }

      def take: UIO[A] =
        Promise.make[Nothing, A].flatMap { promise =>
          state.modify {
            case State(queue, waitingConsumers, waitingProducers) =>
              if (queue.nonEmpty) {
                // Queue has items
                val (item, remainingQueue) = queue.dequeue

                if (waitingProducers.nonEmpty) {
                  // There are waiting producers, take their item and let them proceed
                  val ((producerItem, producerPromise), remainingProducers) =
                    waitingProducers.dequeue
                  val effect   = producerPromise.succeed(()).as(item)
                  val newQueue = remainingQueue.enqueue(producerItem)
                  (
                    effect,
                    State(newQueue, waitingConsumers, remainingProducers)
                  )
                } else {
                  // No waiting producers
                  (
                    ZIO.succeed(item),
                    State(remainingQueue, waitingConsumers, waitingProducers)
                  )
                }
              } else {
                // Queue is empty, consumer must wait
                val effect = promise.await
                (
                  effect,
                  State(
                    queue,
                    waitingConsumers.enqueue(promise),
                    waitingProducers
                  )
                )
              }
          }.flatten
        }

      // Additional utility methods
      def size: UIO[Int] =
        state.get.map(_.queue.size)

      def isEmpty: UIO[Boolean] =
        state.get.map(_.queue.isEmpty)

      def isFull: UIO[Boolean] =
        state.get.map(_.queue.size >= capacity)
    }

    object BoundedQueue {

      case class State[A](
        queue: scala.collection.immutable.Queue[A],
        waitingConsumers: scala.collection.immutable.Queue[Promise[Nothing, A]],
        waitingProducers: scala.collection.immutable.Queue[
          (A, Promise[Nothing, Unit])
        ]
      )

      def make[A](capacity: Int): UIO[BoundedQueue[A]] =
        if (capacity <= 0)
          ZIO.die(new IllegalArgumentException("capacity must be positive"))
        else
          Ref
            .make(
              State[A](
                scala.collection.immutable.Queue.empty,
                scala.collection.immutable.Queue.empty,
                scala.collection.immutable.Queue.empty
              )
            )
            .map(BoundedQueue(capacity, _))
    }

    trait Queue[A] {
      def offer(a: A): UIO[Unit]

      def take: UIO[A]
    }

    object Queue {
      def make[A](capacity: Int): UIO[Queue[A]] =
        BoundedQueue.make(capacity)
    }

    // Example usage
    object BoundedQueueExample extends ZIOAppDefault {
      def run =
        for {
          queue <- BoundedQueue.make[Int](3)

          // Producer fiber that will block when the queue is full
          producer <- ZIO
                        .foreachPar(1 to 5) { i =>
                          for {
                            _ <- ZIO.debug(s"Offering $i")
                            _ <- queue.offer(i)
                            _ <- ZIO.debug(s"Offered $i successfully")
                          } yield ()
                        }
                        .fork

          consumer <- ZIO
                        .foreachPar(1 to 5) { _ =>
                          for {
                            _ <- ZIO.debug("Taking from queue...")
                            v <- queue.take
                            _ <- ZIO.debug(s"Took $v from queue")
                            _ <- ZIO.sleep(500.millis)
                          } yield ()
                        }
                        .fork

          _ <- producer.join
          _ <- consumer.join

          _ <- ZIO.debug("All done!")
        } yield ()
    }

  }

}
