package zionomicon.solutions

import zio._

package ZIORuntime {

  /**
   *   1. For educational purposes, try to write a ZIO application that
   *      monopolizes all workers of the default executor.
   */
  package MonopolizeWorkers {

    object MonopolizeWorkers extends ZIOAppDefault {

      // A synchronous tight loop that blocks the underlying worker thread.
      // Because the loop never returns control to the ZIO runtime, cooperative
      // yielding cannot interrupt it — the thread is simply stuck inside the
      // `ZIO.succeed` thunk until the JVM process exits.
      val greedyTask: UIO[Long] =
        ZIO.succeed {
          var count = 0L
          while (true) count += 1
          count
        }

      // A heartbeat that tries to print every second.  Once every worker thread
      // is occupied by a greedy task, new runnables (including the timer
      // callback that would resume this fiber after `ZIO.sleep`) cannot be
      // picked up, so the heartbeat will be completely starved.
      val heartbeat: UIO[Nothing] =
        (ZIO.debug("heartbeat – still alive") *> ZIO.sleep(1.second)).forever

      def run =
        for {
          numWorkers <- ZIO.succeed(java.lang.Runtime.getRuntime.availableProcessors())
          _          <- ZIO.debug(s"Default executor has $numWorkers worker threads.")
          _          <- ZIO.debug(s"Forking $numWorkers greedy fibers…")
          // Fork one tight-loop fiber per worker thread so every thread is occupied.
          _          <- ZIO.foreachParDiscard(1 to numWorkers)(_ => greedyTask).fork
          // Give the greedy fibers a moment to start and claim their threads.
          _          <- ZIO.sleep(500.millis)
          _          <- ZIO.debug("All workers should now be monopolized.")
          _          <- ZIO.debug("Starting heartbeat — expect silence from here on:")
          // The heartbeat will never print again because no worker is free to
          // resume this fiber after the first `ZIO.sleep`.
          _ <- heartbeat
        } yield ()
    }

  }

  /**
   *   2. Instead of using the default executor, try using one of the
   *      out-of-the-box schedulers — for example, the
   *      [[https://github.com/getkyo/kyo/tree/main/kyo-scheduler Kyo
   *      scheduler]] — to run a ZIO application. To see the difference, write
   *      a JMH benchmark that compares the throughput of the same workload
   *      running under the default `ZScheduler` versus the alternative
   *      scheduler.
   */
  package SchedulerBenchmark {}

}
