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
    case class NaiveTMap[K, V] private (private val map: TRef[Map[K, V]]) {
      def put(key: K, value: V): STM[Nothing, Unit] =
        map.update(_ + (key -> value))

      def get(key: K): STM[Nothing, Option[V]] =
        map.get.map(_.get(key))
    }

    object NaiveTMap {
      def empty[K, V]: UIO[NaiveTMap[K, V]] =
        TRef.make(Map.empty[K, V]).commit.map(NaiveTMap(_))
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
    /**
     * JMH Benchmark comparing implementations
     *
     * Run all benchmarks: sbt "Jmh / run"
     *
     * Run only StmPerformanceBenchmark: sbt "Jmh / run"
     * ".*StmPerformanceBenchmark.*"
     *
     * Run a specific benchmark method: sbt "Jmh / run"
     * ".*StmPerformanceBenchmark.shardedTMap16"
     *
     * This demonstrates how sharding dramatically reduces contention by
     * distributing keys across independent TRef buckets.
     */
    object EfficientTMapBenchmark extends ZIOAppDefault {
      val run = Console.printLine(
        "JMH Benchmarks available. Run with: sbt \"Jmh / run\" \".*StmPerformanceBenchmark.*\""
      )
    }
  }

}
