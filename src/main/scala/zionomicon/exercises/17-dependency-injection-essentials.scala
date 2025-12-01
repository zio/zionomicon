package zionomicon.exercises

package DependencyInjectionEssentials {

  /**
   *   1. What is the purpose of `Tag[A]`, which is commonly used in the ZIO
   *      library, particularly in the `ZEnvironment` operators?
   */
  package TagPurposeAndUsage {}

  /**
   *   2. In this chapter, we introduced services that were monomorphic, meaning
   *      they operate on a single type. Now, let’s create a key-value store
   *      service that can store and retrieve values of any type using the
   *      service pattern. Ensure that the service is polymorphic over the types
   *      of keys and values by implementing the following trait:
   *
   * {{{
   * trait KeyValueStore[K, V, E, F[_, _]] {
   *   def get(key: K): F[E, V]
   *   def set(key: K, value: V): F[E, V]
   *   def remove(key: K): F[E, Unit]
   * }
   * }}}
   *
   * To implement this service, you will need a deep understanding of the
   * `Tag[A]` and `LightTypeTag` types, which are essential for the
   * `ZEnvironment` operators.
   */
  package PolymorphicKeyValueStoreImpl {}

  /**
   *   3. Implement a heterogeneous key-value store service that can store and
   *      retrieve values of different types using type-safe keys in the same
   *      store instance. The keys should carry type information to ensure type
   *      safety during retrieval. Use the following trait as a starting point:
   *
   * {{{
   * trait TypedKey[V] {
   *   def name: String
   * }
   *
   * trait HeterogeneousStore {
   *   def get[V: Tag](key: TypedKey[V]): IO[Throwable, V]
   *   def set[V: Tag](key: TypedKey[V], value: V): IO[Throwable, V]
   *   def remove[V: Tag](key: TypedKey[V]): IO[Throwable, Unit]
   * }
   * }}}
   */
  package HeterogeneousKeyValueStoreImpl {}
}
