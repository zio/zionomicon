package zionomicon.solutions

import zio._
import zio.stm._
import org.openjdk.jmh.annotations.{State, Benchmark => JBenchmark, BenchmarkMode, Mode, OutputTimeUnit, Warmup, Measurement, Fork, Setup, Level}
import java.util.concurrent.TimeUnit

@State(org.openjdk.jmh.annotations.Scope.Benchmark)
@BenchmarkMode(Array(org.openjdk.jmh.annotations.Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
class StmPerformanceBenchmark {
  import StmPerformance.EfficientTMap._

  val numFibers      = 100
  val writesPerFiber = 1000

  private var naive: NaiveTMap[Int, Int]       = _
  private var sharded1: ShardedTMap[Int, Int]  = _
  private var sharded16: ShardedTMap[Int, Int] = _
  private var zioMap: TMap[Int, Int]           = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    Unsafe.unsafe { implicit u =>
      naive = Runtime.default.unsafe
        .run(NaiveTMap.empty[Int, Int])
        .getOrThrowFiberFailure()
      sharded1 = Runtime.default.unsafe
        .run(ShardedTMap.make[Int, Int](numShards = 1))
        .getOrThrowFiberFailure()
      sharded16 = Runtime.default.unsafe
        .run(ShardedTMap.make[Int, Int](numShards = 16))
        .getOrThrowFiberFailure()
      zioMap = Runtime.default.unsafe
        .run(TMap.empty[Int, Int].commit)
        .getOrThrowFiberFailure()
    }
  }

  def runWrites(put: (Int, Int) => UIO[Unit]): Unit = {
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(
          ZIO.foreachParDiscard(0 until numFibers) { fiberIdx =>
            ZIO.foreachDiscard(0 until writesPerFiber) { writeIdx =>
              put(fiberIdx, writeIdx)
            }
          }
        )
        .getOrThrowFiberFailure()
    }
  }

  @JBenchmark
  def naiveTMap(): Unit = {
    runWrites((fi, wi) =>
      naive.put(fi * writesPerFiber + wi, wi).commit
    )
  }

  @JBenchmark
  def shardedTMap1(): Unit = {
    runWrites((fi, wi) =>
      sharded1.put(fi * writesPerFiber + wi, wi).commit
    )
  }

  @JBenchmark
  def shardedTMap16(): Unit = {
    runWrites((fi, wi) =>
      sharded16.put(fi * writesPerFiber + wi, wi).commit
    )
  }

  @JBenchmark
  def zioTMap(): Unit = {
    runWrites((fi, wi) =>
      zioMap.put(fi * writesPerFiber + wi, wi).commit
    )
  }
}
