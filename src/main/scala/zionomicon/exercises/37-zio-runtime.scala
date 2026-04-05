package zionomicon.exercises

package ZIORuntime {

  /**
   *   1. For educational purposes, try to write a ZIO application that
   *      monopolizes all workers of the default executor.
   *
   * Hint: The default `ZScheduler` maintains a fixed pool of worker threads
   * equal to the number of available processors. Submit enough CPU-bound tasks
   * to saturate every worker thread simultaneously and observe the effect on
   * the rest of the application (e.g., a heartbeat fiber that should print
   * periodically).
   *
   * {{{
   * object MonopolizeWorkers extends ZIOAppDefault {
   *   def run = ???
   * }
   * }}}
   */
  package MonopolizeWorkers {}

  /**
   *   2. Instead of using the default executor, try using one of the
   *      out-of-the-box schedulers — for example, the
   *      [[https://github.com/getkyo/kyo/tree/main/kyo-scheduler Kyo scheduler]]
   *      — to run a ZIO application. To see the difference, write a JMH
   *      benchmark that compares the throughput of the same workload running
   *      under the default `ZScheduler` versus the alternative scheduler.
   *
   * Hint: Use `Runtime.setExecutor` in the `bootstrap` of your `ZIOAppDefault`
   * (or in the JMH `@Setup` method) to swap in the alternative executor. A good
   * benchmark workload is a large number of short-lived concurrent fibers
   * (e.g., `ZIO.foreachPar`) so that scheduler overhead is visible in the
   * measurements.
   *
   * {{{
   * // Application using alternative scheduler
   * object KyoSchedulerApp extends ZIOAppDefault {
   *   val kyoExecutor: Executor = ???
   *
   *   override val bootstrap = Runtime.setExecutor(kyoExecutor)
   *
   *   def run = ???
   * }
   *
   * // JMH benchmark skeleton
   * @State(Scope.Benchmark)
   * @BenchmarkMode(Array(Mode.Throughput))
   * @OutputTimeUnit(TimeUnit.SECONDS)
   * class SchedulerBenchmark {
   *   @Benchmark def zScheduler(): Unit = ???
   *   @Benchmark def kyoScheduler(): Unit = ???
   * }
   * }}}
   */
  package SchedulerBenchmark {}

}
