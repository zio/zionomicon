package zionomicon.exercises

import zio._
import zio.test.TestAspect._
import zio.test._

/**
 *   1. Find the right location to insert `ZIO.interruptible` to make the test
 *      succeed:
 *
 *     ```scala
 *     import zio.test.{test, _}
 *     import zio.test.TestAspect._
 *
 *     test("interruptible") {
 *      for {
 *        ref <- Ref.make(0)
 *        latch <- Promise.make[Nothing, Unit]
 *        fiber <- ZIO
 *          .uninterruptible(latch.succeed(()) *> ZIO.never)
 *          .ensuring(ref.update(_ + 1))
 *          .forkDaemon
 *        _ <- Live.live(
 *          latch.await *> fiber.interrupt.disconnect.timeout(1.second)
 *        )
 *        value <- ref.get
 *      } yield assertTrue(value == 1)
 *     } @@ nonFlaky
 *     ```
 */

object Exercise1 extends ZIOSpecDefault {
  override def spec =
    test("interruptible") {
      for {
        ref   <- Ref.make(0)
        latch <- Promise.make[Nothing, Unit]
        fiber <- ZIO
                   .uninterruptible(latch.succeed(()) *> ZIO.never)
                   .ensuring(ref.update(_ + 1))
                   .forkDaemon
        _ <- Live.live(
               latch.await *> fiber.interrupt.disconnect.timeout(1.second)
             )
        value <- ref.get
      } yield assertTrue(value == 1)
    } @@ nonFlaky
}

/**
 *   2. Find the right location to insert `ZIO.uninterruptible` to make the
 *      test succeed:
 *
 *     ```scala mdoc:invisible
 *     import zio.test._
 *     import zio.test.TestAspect._
 *
 *     test("uninterruptible") {
 *       for {
 *         ref   <- Ref.make(0)
 *         latch <- Promise.make[Nothing, Unit]
 *         fiber <- {
 *                    latch.succeed(()) *>
 *                      Live.live(ZIO.sleep(10.millis)) *>
 *                      ref.update(_ + 1)
 *                  }.forkDaemon
 *         _     <- latch.await *> fiber.interrupt
 *         value <- ref.get
 *       } yield assertTrue(value == 1)
 *     } @@ nonFlaky
 *     ```
 */

object Exercise2 extends ZIOSpecDefault {
  def spec =
    test("uninterruptible") {
      for {
        ref   <- Ref.make(0)
        latch <- Promise.make[Nothing, Unit]
        fiber <- {
                   latch.succeed(()) *>
                     Live.live(ZIO.sleep(10.millis)) *>
                     ref.update(_ + 1)
                 }.forkDaemon
        _     <- latch.await *> fiber.interrupt
        value <- ref.get
      } yield assertTrue(value == 1)
    } @@ nonFlaky
}
