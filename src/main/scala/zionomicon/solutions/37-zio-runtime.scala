package zionomicon.solutions

package ZIORuntime {

  /**
   *   1. For educational purposes, try to write a ZIO application that
   *      monopolizes all workers of the default executor.
   */
  package MonopolizeWorkers {}

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
