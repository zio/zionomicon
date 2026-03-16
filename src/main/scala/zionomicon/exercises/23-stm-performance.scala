package zionomicon.exercises

package StmPerformance {

  /**
   * 1. Improve the performance of the red-black tree you implemented in the
   *    previous chapter using fine-grained locking.
   *
   * Hint: Instead of wrapping the entire tree in a single `TRef`, wrap each
   * node in a separate `TRef` as well. You can minimize the chances of
   * conflicts and retries by isolating each node in its own transactional
   * variable.
   */
  package FineGrainedRedBlackTree {}

  /**
   * 2. The first naive implementation of a concurrent map (`TMap`) that comes
   *    to mind is to have a single `TRef` that holds a `Map` of keys and values:
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
   * Implement a more efficient version of `TMap` that updates to different
   * keys in the map are isolated and do not conflict with each other in a
   * concurrent environment.
   */
  package EfficientTMap {}

}
