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
