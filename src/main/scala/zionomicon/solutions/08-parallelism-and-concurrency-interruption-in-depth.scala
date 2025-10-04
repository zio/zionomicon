package zionomicon.solutions

import zio.test.TestAspect._
import zio.test._
import zio._

object ParallelismAndConcurrencyInterruptionInDepth extends ZIOSpecDefault {

  override def spec =
    suite("InterruptionInDepth")(
      test("interruptible") {
        for {
          ref   <- Ref.make(0)
          latch <- Promise.make[Nothing, Unit]
          fiber <- ZIO
                     .uninterruptible(
                       latch.succeed(()) *> ZIO.uninterruptible(ZIO.never)
                     )
                     .ensuring(ref.update(_ + 1))
                     .forkDaemon
          _ <- Live.live(
                 latch.await *> fiber.interrupt.disconnect.timeout(1.second)
               )
          value <- ref.get
        } yield assertTrue(value == 1)
      } @@ nonFlaky
    )
}
