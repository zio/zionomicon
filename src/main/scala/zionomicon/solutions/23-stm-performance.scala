package zionomicon.solutions

package StmPerformance {

  /**
   * The first naive implementation of a concurrent map (`TMap`) that comes to
   * mind is to have a single `TRef` that holds a `Map` of keys and values:
   *
   * {{{
   * case class TMap[K, V] private (private val map: TRef[Map[K, V]]) {
   *   def put(key: K, value: V): STM[Nothing, Unit] =
   *     map.update(_ + (key -> value))
   *
   *   def get(key: K): STM[Nothing, Option[V]] =
   *     map.get.map(_.get(key))
   * }
   * }}}
   *
   * However, this approach can lead to contention and conflicts when multiple
   * transactions try to update different keys in the map simultaneously.
   * Implement a more efficient version of `TMap` that updates to different keys
   * in the map are isolated and do not conflict with each other in a concurrent
   * environment.
   */
  package EfficientTMap {
    import zio._
    import zio.stm._

    /**
     * Naive concurrent map implementation using a single TRef.
     *
     * ⚠️ Performance Issue: All transactions contend on the same TRef, causing
     * frequent conflicts even when updating different keys. For example, if 100
     * fibers each write to different keys, all 100 transactions must retry
     * repeatedly because they're all modifying the same shared reference.
     */
    case class TMap[K, V] private (private val map: TRef[Map[K, V]]) {
      def put(key: K, value: V): STM[Nothing, Unit] =
        map.update(_ + (key -> value))

      def get(key: K): STM[Nothing, Option[V]] =
        map.get.map(_.get(key))
    }

    object TMap {
      def empty[K, V]: UIO[TMap[K, V]] =
        TRef.make(Map.empty[K, V]).commit.map(TMap(_))
    }

    /**
     * Efficient concurrent map using sharding.
     *
     * ✓ Performance Insight: Instead of one TRef for all keys, we partition the
     * keyspace across multiple independent TRefs (shards). Each fiber writing
     * to a different key hits a different shard, so there's no false
     * contention.
     *
     * With 16 shards and 100 fibers writing to distinct keys:
     *   - Naive: ~100 fibers all retry on the same TRef
     *   - Sharded: ~6 fibers per shard, much lower contention per TRef
     *
     * This reduces conflicts by a factor proportional to numShards.
     */
    final class ShardedTMap[K, V] private (
      private val shards: Vector[TRef[Map[K, V]]],
      private val numShards: Int
    ) {
      private def shardIndex(key: K): Int =
        (key.hashCode() & 0x7fffffff) % numShards

      def put(key: K, value: V): USTM[Unit] =
        shards(shardIndex(key)).update(_ + (key -> value))

      def get(key: K): USTM[Option[V]] =
        shards(shardIndex(key)).get.map(_.get(key))
    }

    object ShardedTMap {
      def make[K, V](numShards: Int = 16): UIO[ShardedTMap[K, V]] =
        ZIO
          .collectAll(
            Vector.fill(numShards)(TRef.make(Map.empty[K, V]).commit)
          )
          .map(shards => new ShardedTMap(shards, numShards))
    }

    /**
     * Benchmark comparing naive, sharded, and ZIO's built-in TMap
     * implementations.
     *
     * Setup:
     *   - 100 concurrent fibers
     *   - Each fiber writes 1000 distinct keys (no overlap across fibers)
     *   - Total: 100,000 transactional writes
     *
     * Implementations:
     *   - NaiveTMap: Single TRef wrapping Map (demonstrates contention problem)
     *   - ShardedTMap: 16 independent TRef shards (fine-grained locking
     *     solution)
     *   - ZIO's TMap: Hash table with chaining (built-in library
     *     implementation)
     *
     * Expected Result: ShardedTMap and ZIO's TMap should significantly
     * outperform NaiveTMap due to reduced contention on independent
     * buckets/shards.
     */
    object EfficientTMapBenchmark extends ZIOAppDefault {
      val numFibers      = 100
      val writesPerFiber = 1000

      /**
       * Benchmark harness: times an operation and reports elapsed time +
       * ops/sec
       */
      def benchmark(name: String)(run: UIO[Unit]): UIO[Unit] =
        for {
          start    <- Clock.nanoTime
          _        <- run
          end      <- Clock.nanoTime
          elapsed   = (end - start) / 1_000_000L
          total     = (numFibers * writesPerFiber).toLong
          opsPerSec = total * 1_000L / math.max(elapsed, 1L)
          _ <- Console
                 .printLine(
                   s"[$name] ${elapsed}ms | ${opsPerSec} ops/sec"
                 )
                 .orDie
        } yield ()

      /**
       * Run concurrent fibers, each executing sequential writes.
       *
       * Layout:
       *   - 100 fibers execute in parallel (ZIO.foreachParDiscard)
       *   - Each fiber sequentially writes 1000 times (ZIO.foreachDiscard)
       *   - Keys are distinct per fiber: fiberIdx * writesPerFiber + writeIdx
       */
      def runWrites(put: (Int, Int) => UIO[Unit]): UIO[Unit] =
        ZIO.foreachParDiscard(0 until numFibers) { fiberIdx =>
          ZIO.foreachDiscard(0 until writesPerFiber) { writeIdx =>
            put(fiberIdx, writeIdx)
          }
        }

      val run =
        for {
          naive   <- TMap.empty[Int, Int]
          sharded <- ShardedTMap.make[Int, Int]()
          zioTMap <- zio.stm.TMap.empty[Int, Int].commit
          _ <- Console
                 .printLine(
                   s"Benchmark: $numFibers fibers x $writesPerFiber writes each"
                 )
                 .orDie
          _ <- benchmark("NaiveTMap       ") {
                 runWrites((fi, wi) =>
                   naive.put(fi * writesPerFiber + wi, wi).commit
                 )
               }
          _ <- benchmark("ShardedTMap     ") {
                 runWrites((fi, wi) =>
                   sharded.put(fi * writesPerFiber + wi, wi).commit
                 )
               }
          _ <- benchmark("ZIO's TMap      ") {
                 runWrites((fi, wi) =>
                   zioTMap.put(fi * writesPerFiber + wi, wi).commit
                 )
               }
        } yield ()
    }
  }

}
