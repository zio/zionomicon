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

}
