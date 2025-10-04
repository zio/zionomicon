package zionomicon.solutions

import zio.test.TestAspect._
import zio.test._
import zio._

import zio._
import zio.test.TestAspect._
import zio.test._

package ParallelismAndConcurrencyInterruptionInDepth {

  /**
   * Find the right location to insert `ZIO.interruptible` to make the test
   * succeed.
   */
  object Exercise1 extends ZIOSpecDefault {

    override def spec =
      test("interruptible") {
        for {
          ref   <- Ref.make(0)
          latch <- Promise.make[Nothing, Unit]
          fiber <- ZIO
                     .uninterruptible(
                       latch.succeed(()) *> ZIO.interruptible(ZIO.never)
                     )
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
   * Find the right location to insert `ZIO.uninterruptible` to make the test
   * succeed.
   */
  object Exercise2 extends ZIOSpecDefault {
    override def spec =
      test("uninterruptible") {
        for {
          ref   <- Ref.make(0)
          latch <- Promise.make[Nothing, Unit]
          fiber <- ZIO.uninterruptible {
                     latch.succeed(()) *>
                       Live.live(ZIO.sleep(10.millis)) *>
                       ref.update(_ + 1)
                   }.forkDaemon
          _     <- latch.await *> fiber.interrupt
          value <- ref.get
        } yield assertTrue(value == 1)
      } @@ nonFlaky

  }

  /**
   * Implement `withFinalizer` without using `ZIO#ensuring`. If the given zio
   * effect has not started, it can be interrupted. However, once it has
   * started, the finalizer must be executed regardless of whether the `zio`
   * effect succeeds, fails, or is interrupted:
   *
   * {{{
   *   def withFinalizer[R, E, A](
   *     zio: ZIO[R, E, A]
   *   )(finalizer: UIO[Any]): ZIO[R, E, A] = ???
   * }}}
   *
   * Write a test that checks the proper execution of the `finalizer` in the
   * case the given `zio` effect is interrupted.
   *
   * Hint: use the `uninterruptibleMask` primitive to implement `withFinalizer`.
   */
  object Exercise3 extends ZIOSpecDefault {
    override def spec =
      test("withFinalizer executes finalizer when zio is interrupted") {
        for {
          finalizerExecuted <- Ref.make(false)
          effectStarted     <- Promise.make[Nothing, Unit]
          fiber <- withFinalizer(effectStarted.succeed(()) *> ZIO.never)(
                     finalizerExecuted.set(true)
                   ).fork
          _      <- effectStarted.await // Wait for the effect to start
          _      <- fiber.interrupt     // Interrupt the fiber
          result <- finalizerExecuted.get
        } yield assertTrue(result)
      } @@ nonFlaky

    def withFinalizer[R, E, A](
      zio: ZIO[R, E, A]
    )(finalizer: UIO[Any]): ZIO[R, E, A] =
      ZIO.uninterruptibleMask { restore =>
        restore(zio).exit.flatMap { exit =>
          finalizer *> ZIO.suspendSucceed(exit)
        }
      }
  }

  /**
   * Implement the `ZIO#disconnect` with the stuff you have learned in this
   * chapter, then compare your implementation with the one in ZIO.
   */
  object Exercise4 {
    def disconnect[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      ZIO.uninterruptibleMask(restore =>
        restore(zio).forkDaemon.flatMap(f =>
          restore(f.join).onInterrupt(f.interruptFork)
        )
      )
  }

}
