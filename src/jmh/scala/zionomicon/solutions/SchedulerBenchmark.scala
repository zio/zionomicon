package zionomicon.solutions

import zio._
import kyo.scheduler.Scheduler
import org.openjdk.jmh.annotations.{
  Benchmark => JBenchmark,
  BenchmarkMode,
  Fork,
  Level,
  Measurement,
  Mode,
  OutputTimeUnit,
  Scope,
  Setup,
  State,
  Warmup
}
import java.util.concurrent.TimeUnit

// =============================================================================
// Benchmark results
// Environment: JDK 21.0.9 (OpenJDK 64-Bit Server VM), NixOS, Linux 6.9.7
// Run command: sbt "jmh:run -i 5 -wi 3 -f 1 .*SchedulerBenchmark.*"
// Warmup: 3 iterations × 2 s  |  Measurement: 5 iterations × 3 s  |  Fork: 1
//
// Benchmark                         Mode  Cnt     Score     Error  Units
// SchedulerBenchmark.zScheduler    thrpt    5  1745.462 ± 600.145  ops/s
// SchedulerBenchmark.kyoScheduler  thrpt    5  1181.294 ± 707.480  ops/s
//
// ZScheduler is ~48% faster on this workload (1745 vs 1181 ops/s).
//
// Analysis
// --------
// 1. Error bars are wide (±600 and ±707) — the confidence intervals overlap
//    substantially, so the difference is directional but not definitive at
//    this sample size.  Running with more forks (-f 3) and more iterations
//    would tighten them.
//
// 2. The workload is ZIO-native.  ZIO.foreachParDiscard with 1 000 short-lived
//    fibers plays to ZScheduler's strengths — it is designed specifically to
//    minimise ZIO fiber fork/join overhead.  The Kyo scheduler is designed for
//    Kyo's own fiber model; wrapping it via Executor.fromJavaExecutor adds a
//    thin translation layer and loses Kyo-specific optimisations.
//
// 3. Kyo's first warmup iteration is noticeably slow (842 vs 748 ops/s for
//    ZScheduler) but it stabilises faster in subsequent iterations — its
//    adaptive concurrency regulator takes a few rounds to calibrate.
//
// 4. This is not the workload Kyo was designed for.  Kyo's scheduler shines
//    when paired with its own async/structured-concurrency primitives.  A
//    fairer comparison would run equivalent workloads natively in both
//    runtimes rather than forcing Kyo to serve as a drop-in executor for
//    ZIO fibers.
// =============================================================================

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class SchedulerBenchmark {

  // Workload: 1 000 short-lived concurrent fibers, each doing a small
  // CPU-bound computation. This exercises scheduler fork/join throughput
  // rather than raw compute, making scheduler differences visible.
  private val numFibers = 1000

  private val workload: UIO[Unit] =
    ZIO.foreachParDiscard(1 to numFibers) { _ =>
      ZIO.succeed {
        var sum = 0
        var i   = 1
        while (i <= 100) { sum += i; i += 1 }
        sum
      }
    }

  // Runtime backed by ZIO's built-in ZScheduler (default).
  private var zSchedulerRuntime: Runtime[Any] = _

  // Runtime backed by the Kyo scheduler.
  private var kyoRuntime: Runtime[Any] = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    zSchedulerRuntime = Runtime.default

    val kyoExecutor =
      Executor.fromJavaExecutor(Scheduler.get.asExecutor)

    kyoRuntime = Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(ZIO.runtime[Any].provide(Runtime.setExecutor(kyoExecutor)))
        .getOrThrowFiberFailure()
    }
  }

  private def runWith(runtime: Runtime[Any]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(workload).getOrThrowFiberFailure()
    }

  @JBenchmark
  def zScheduler(): Unit = runWith(zSchedulerRuntime)

  @JBenchmark
  def kyoScheduler(): Unit = runWith(kyoRuntime)
}
