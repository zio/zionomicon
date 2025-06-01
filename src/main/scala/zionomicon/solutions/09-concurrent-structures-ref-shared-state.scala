package zionomicon.solutions

package RefSharedState {

  /**
   *   1. Write a simple `Counter` with the following interface that can be
   *      incremented and decremented concurrently:
   *
   *     ```scala
   *     trait Counter {
   *       def increment: UIO[Long]
   *       def decrement: UIO[Long]
   *       def get: UIO[Long]
   *       def reset: UIO[Unit]
   *     }
   *     ```
   */
  object CounterImpl {

    import zio._

    class Counter private (private val ref: Ref[Long]) {
      def increment: UIO[Long] = ref.updateAndGet(_ + 1)
      def decrement: UIO[Long] = ref.updateAndGet(_ - 1)
      def get: UIO[Long]       = ref.get
      def reset: UIO[Unit]     = ref.set(0L)
    }

    object Counter {
      def make: UIO[Counter] = Ref.make(0L).map(new Counter(_))
    }

  }

  /**
   *   2. Implement a bounded queue using `Ref` that has a maximum capacity that
   *      supports the following interface:
   *
   *     ```scala
   *     trait BoundedQueue[A] {
   *       def enqueue(a: A): UIO[Boolean] // Returns false if queue is full
   *       def dequeue: UIO[Option[A]]     // Returns None if queue is empty
   *       def size: UIO[Int]
   *       def capacity: UIO[Int]
   *     }
   *     ```
   */
  object BoundedQueueImpl {
    import zio._

    import scala.collection.immutable.Queue

    case class BoundedQueue[A](
      enqueue: A => UIO[Boolean],
      dequeue: UIO[Option[A]],
      size: UIO[Int],
      capacity: UIO[Int]
    )

    object BoundedQueue {
      def make[A](maxCapacity: Int): UIO[BoundedQueue[A]] =
        for {
          queueRef <- Ref.make(Queue.empty[A])
        } yield BoundedQueue[A](
          enqueue = (a: A) =>
            queueRef.modify { queue =>
              if (queue.size >= maxCapacity) {
                (false, queue)
              } else {
                (true, queue.enqueue(a))
              }
            },
          dequeue = queueRef.modify { queue =>
            queue.dequeueOption match {
              case Some((element, newQueue)) => (Some(element), newQueue)
              case None                      => (None, queue)
            }
          },
          size = queueRef.get.map(_.size),
          capacity = ZIO.succeed(maxCapacity)
        )
    }
  }

  /**
   *   3. Write a `CounterManager` service that manages multiple counters with
   *      the following interface:
   *
   *     ```scala mdoc:invisible
   *     type CounterId = String
   *
   *     trait CounterManager {
   *       def increment(id: CounterId): UIO[Int]
   *       def decrement(id: CounterId): UIO[Int]
   *       def get(id: CounterId): UIO[Int]
   *       def reset(id: CounterId): UIO[Unit]
   *       def remove(id: CounterId): UIO[Unit]
   *     }
   *     ```
   */

  object CounterManagerImpl {
    import zio._

    type CounterId = String

    case class CounterManager private (
      private val countersRef: Ref[Map[CounterId, Int]]
    ) {
      def increment(id: CounterId): UIO[Int] =
        countersRef.modify { counters =>
          val newValue = counters.getOrElse(id, 0) + 1
          (newValue, counters.updated(id, newValue))
        }

      def decrement(id: CounterId): UIO[Int] =
        countersRef.modify { counters =>
          val newValue = counters.getOrElse(id, 0) - 1
          (newValue, counters.updated(id, newValue))
        }

      def get(id: CounterId): UIO[Int] =
        countersRef.get.map(_.getOrElse(id, 0))

      def reset(id: CounterId): UIO[Unit] =
        countersRef.update(_.updated(id, 0))

      def remove(id: CounterId): UIO[Unit] =
        countersRef.update(_ - id)
    }

    object CounterManager {
      def make: UIO[CounterManager] =
        Ref.make(Map.empty[CounterId, Int]).map(CounterManager(_))
    }
  }

  /**
   *   4. Implement a basic log renderer for the `FiberRef[Log]` we have defined
   *      through the chapter. It should show the hierarchical structure of
   *      fiber logs using indentation:
   *
   *       - Each level of nesting should be indented by two spaces from the
   *         previous one.
   *       - The log entries for each fiber should be shown on separate lines
   *       - Child fiber logs should be shown under their parent fiber
   *
   *  ```scala
   *    trait Logger {
   *      def log(message: String): UIO[Unit]
   *    }
   *
   *    object Logger {
   *      def render(ref: Log): UIO[String] = ???
   *    }
   *    ```
   *
   * Example output:
   *
   *     ```scala
   *     Got foo
   *       Got 1
   *       Got 2
   *     Got bar
   *       Got 3
   *       Got 4
   *     ````
   */
  import zio._

  object NestedLoggerRendererImpl {

    sealed trait LogEntry

    case class Message(content: String) extends LogEntry

    case class Child(entries: Chunk[LogEntry]) extends LogEntry

    case class Logger private (private val logs: FiberRef[Chunk[LogEntry]]) {
      def log(message: String): UIO[Unit] =
        logs.update(_ :+ Message(message))

      def render: ZIO[Any, Nothing, String] = {
        def renderLog(log: Chunk[LogEntry], indent: Int): Chunk[String] = {
          val indentStr = " " * indent
          log.flatMap {
            case Message(content) => Chunk(indentStr + content)
            case Child(childLog)  => renderLog(childLog, indent + 2)
          }
        }

        logs.get.map(renderLog(_, 0).mkString("\n"))
      }
    }

    object Logger {
      def make: ZIO[Any, Nothing, Logger] =
        ZIO.scoped {
          FiberRef
            .make[Chunk[LogEntry]](
              initial = Chunk.empty,
              fork = _ => Chunk.empty,
              join = (parent, child) => parent ++ Chunk(Child(child))
            )
            .map(Logger(_))
        }
    }
  }

  object Main extends ZIOAppDefault {
    import NestedLoggerRendererImpl._

    def run =
      for {
        logger <- Logger.make
        _      <- logger.log("Starting application...")
        _ <- {
               for {
                 _ <- logger.log("Initializing components...")
                 _ <- logger.log("Configuring components...")
                 _ <- logger.log("Setting up services...")
               } yield ()
             }.fork.flatMap(_.join)
        _   <- logger.log("Application started successfully.")
        log <- logger.render
        _   <- Console.printLine(log)
      } yield ()
  }

}
