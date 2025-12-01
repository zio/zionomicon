package zionomicon.solutions

import izumi.reflect.Tag
import zio._

package DependencyInjectionEssentials {

  /**
   *   1. What is the purpose of `Tag[A]`, which is commonly used in the ZIO
   *      library, particularly in the `ZEnvironment` operators?
   */
  package TagPurposeAndUsage {

    /**
     * `Tag[A]` is a type tag that provides runtime type information for type
     * `A`. It solves the "type erasure" problem which is essential for ZIO's
     * dependency injection system:
     *
     *   1. TYPE ERASURE PROBLEM:
     *      - JVM erases generic type information at runtime
     *      - Without Tag, `ZEnvironment[String]` and `ZEnvironment[Int]` would
     *        be indistinguishable at runtime
     *   2. DEPENDENCY LOOKUP:
     *      - ZEnvironment stores services in a type-indexed map
     *      - Tag provides the key to lookup services by their exact type
     *      - Enables type-safe retrieval: `ZIO.service[MyService]`
     *   3. HOW IT WORKS:
     *      - Tag is implemented using LightTypeTag from the izumi-reflect
     *        library
     *      - It captures full generic type information including type
     *        parameters
     *      - So the Tag[List[String]] is distinguishable from Tag[List[Int]]
     */
  }

  /**
   *   2. In this chapter, we introduced services that were monomorphic, meaning
   *      they operate on a single type. Now, let's create a key-value store
   *      service that can store and retrieve values of any type using the
   *      service pattern. Ensure that the service is polymorphic over the types
   *      of keys and values.
   */
  package PolymorphicKeyValueStoreImpl {
    trait KeyValueStore[K, V, E, F[_, _]] {
      def get(key: K): F[E, V]
      def set(key: K, value: V): F[E, V]
      def remove(key: K): F[E, Unit]
    }

    class PolymorphicKeyValueStore[K, V: Tag](
      ref: Ref[Map[K, V]]
    ) extends KeyValueStore[K, V, Throwable, IO] {

      def get(key: K): IO[Throwable, V] =
        ref.get.flatMap { map =>
          ZIO
            .fromOption(map.get(key))
            .orElseFail(new NoSuchElementException(s"Key not found: $key"))
        }

      def set(key: K, value: V): IO[Throwable, V] =
        ref.update(_ + (key -> value)).as(value)

      def remove(key: K): IO[Throwable, Unit] =
        ref.update(_ - key)
    }

    object PolymorphicKeyValueStore {
      def layer[K: Tag, V: Tag]
        : ZLayer[Any, Nothing, KeyValueStore[K, V, Throwable, IO]] =
        ZLayer {
          Ref.make(Map.empty[K, V]).map { ref =>
            new PolymorphicKeyValueStore[K, V](ref)
          }
        }
    }

    object PolymorphicKeyValueStoreExample extends ZIOAppDefault {
      case class User(id: Int, name: String)

      val program
        : ZIO[KeyValueStore[String, User, Throwable, IO], Throwable, Unit] =
        for {
          store <- ZIO.service[KeyValueStore[String, User, Throwable, IO]]
          _     <- store.set("user:1", User(1, "Alice"))
          user  <- store.get("user:1")
          _     <- Console.printLine(s"Retrieved user: $user")
        } yield ()

      def run = program.provide(PolymorphicKeyValueStore.layer[String, User])
    }

  }

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
  package HeterogeneousKeyValueStoreImpl {

    trait TypedKey[V] {
      def name: String
    }

    object TypedKey {
      def apply[V: Tag](keyName: String): TypedKey[V] = new TypedKey[V] {
        val name: String = keyName

        override def equals(obj: Any): Boolean = obj match {
          case other: TypedKey[_] => name == other.name
          case _                  => false
        }

        override def hashCode(): Int = name.hashCode
      }
    }

    trait HeterogeneousStore {
      def get[V: Tag](key: TypedKey[V]): IO[Throwable, V]
      def set[V: Tag](key: TypedKey[V], value: V): IO[Throwable, V]
      def remove[V: Tag](key: TypedKey[V]): IO[Throwable, Unit]
    }

    object HeterogeneousStore {

      case class InMemory(ref: Ref[Map[String, Any]])
          extends HeterogeneousStore {

        def get[V: Tag](key: TypedKey[V]): IO[Throwable, V] =
          ref.get.flatMap { map =>
            ZIO
              .fromOption(map.get(key.name).map(_.asInstanceOf[V]))
              .orElseFail(
                new NoSuchElementException(s"Key not found: ${key.name}")
              )
          }

        def set[V: Tag](key: TypedKey[V], value: V): IO[Throwable, V] =
          ref.update(_ + (key.name -> value)).as(value)

        def remove[V: Tag](key: TypedKey[V]): IO[Throwable, Unit] =
          ref.update(_ - key.name)
      }

      val inMemory: ZLayer[Any, Nothing, HeterogeneousStore] =
        ZLayer {
          Ref.make(Map.empty[String, Any]).map(InMemory(_))
        }

      def get[V: Tag](key: TypedKey[V]): ZIO[HeterogeneousStore, Throwable, V] =
        ZIO.serviceWithZIO[HeterogeneousStore](_.get(key))

      def set[V: Tag](
        key: TypedKey[V],
        value: V
      ): ZIO[HeterogeneousStore, Throwable, V] =
        ZIO.serviceWithZIO[HeterogeneousStore](_.set(key, value))

      def remove[V: Tag](
        key: TypedKey[V]
      ): ZIO[HeterogeneousStore, Throwable, Unit] =
        ZIO.serviceWithZIO[HeterogeneousStore](_.remove(key))
    }

    object KeyValueStoreExamples extends ZIOAppDefault {

      case class User(id: Int, name: String)

      val program: ZIO[HeterogeneousStore, Throwable, Unit] = {
        val userKey  = TypedKey[User]("current-user")
        val countKey = TypedKey[Int]("login-count")
        val tokenKey = TypedKey[String]("session-token")

        for {
          _ <- HeterogeneousStore.set(userKey, User(1, "Bob"))
          _ <- HeterogeneousStore.set(countKey, 5)
          _ <- HeterogeneousStore.set(tokenKey, "abc123")

          user  <- HeterogeneousStore.get(userKey)
          count <- HeterogeneousStore.get(countKey)
          token <- HeterogeneousStore.get(tokenKey)

          _ <- Console.printLine(s"User: $user, Count: $count, Token: $token")
        } yield ()
      }

      def run = program.provide(HeterogeneousStore.inMemory)
    }
  }
}
