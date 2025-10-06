package zionomicon.solutions

package RefSharedState {

  /**
   *   1. Write a simple `Counter` with the following interface that can be
   *      incremented and decremented concurrently:
   *
   *     {{{
   *     trait Counter {
   *       def increment: UIO[Long]
   *       def decrement: UIO[Long]
   *       def get: UIO[Long]
   *       def reset: UIO[Unit]
   *     }
   *     }}}
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
   *     {{{
   *     trait BoundedQueue[A] {
   *       def enqueue(a: A): UIO[Boolean] // Returns false if queue is full
   *       def dequeue: UIO[Option[A]]     // Returns None if queue is empty
   *       def size: UIO[Int]
   *       def capacity: UIO[Int]
   *     }
   *     }}}
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
   *     {{{
   *     type CounterId = String
   *
   *     trait CounterManager {
   *       def increment(id: CounterId): UIO[Int]
   *       def decrement(id: CounterId): UIO[Int]
   *       def get(id: CounterId): UIO[Int]
   *       def reset(id: CounterId): UIO[Unit]
   *       def remove(id: CounterId): UIO[Unit]
   *     }
   *     }}}
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
   *    {{{
   *    trait Logger {
   *      def log(message: String): UIO[Unit]
   *    }
   *
   *    object Logger {
   *      def render(ref: Log): UIO[String] = ???
   *    }
   *    }}}
   *
   * Example output:
   *
   *     {{{
   *     Got foo
   *       Got 1
   *       Got 2
   *     Got bar
   *       Got 3
   *       Got 4
   *     }}}
   */
  import zio._

  object NestedLoggerRendererImpl {

    sealed trait LogNode

    case class Message(content: String) extends LogNode

    case class Child(entries: Chunk[LogNode]) extends LogNode

    case class Logger private (private val logs: FiberRef[Chunk[LogNode]]) {
      def log(message: String): UIO[Unit] =
        logs.update(_ :+ Message(message))

      def render: ZIO[Any, Nothing, String] = {
        def renderLog(log: Chunk[LogNode], indent: Int): Chunk[String] = {
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
            .make[Chunk[LogNode]](
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

  /**
   *   5. Change the log model and use a more detailed one instead of just a
   *      `String`, so that you can implement an advanced log renderer that adds
   *      timestamps and fiber IDs, like the following output:
   *
   *     {{{
   *     [2024-01-01 10:00:01][fiber-1] Child foo
   *       [2024-01-01 10:00:02][fiber-2] Got 1
   *       [2024-01-01 10:00:03][fiber-2] Got 2
   *     [2024-01-01 10:00:01][fiber-1] Child bar
   *       [2024-01-01 10:00:02][fiber-3] Got 3
   *       [2024-01-01 10:00:03][fiber-3] Got 4
   *     }}}
   *
   * Hint: You can use the following model for the log entry:
   *
   *     {{{
   *     case class LogEntry(
   *       timestamp: java.time.Instant,
   *       fiberId: String,
   *       message: String
   *     )
   *     }}}
   */
  object AdvancedNestedLoggerImpl {
    import java.time.{Instant, ZoneId}
    import java.time.format.DateTimeFormatter

    case class LogEntry(timestamp: Instant, fiberId: String, message: String)

    sealed trait LogNode

    case class Message(entry: LogEntry) extends LogNode

    case class Child(entries: Chunk[LogNode]) extends LogNode

    case class Logger private (private val logs: FiberRef[Chunk[LogNode]]) {
      def log(message: String): UIO[Unit] =
        for {
          timestamp <- Clock.instant
          fiberId   <- ZIO.fiberId.map(_.id.toString).map(id => s"fiber-$id")
          entry      = LogEntry(timestamp, fiberId, message)
          _         <- logs.update(_ :+ Message(entry))
        } yield ()

      def render: ZIO[Any, Nothing, String] = {
        val formatter = DateTimeFormatter
          .ofPattern("yyyy-MM-dd HH:mm:ss")
          .withZone(ZoneId.systemDefault())

        def renderLog(log: Chunk[LogNode], indent: Int): Chunk[String] = {
          val indentStr = " " * indent
          log.flatMap {
            case Message(LogEntry(timestamp, fiberId, message)) =>
              val timestampStr = formatter.format(timestamp)
              Chunk(s"$indentStr[$timestampStr][$fiberId] $message")
            case Child(childLog) =>
              renderLog(childLog, indent + 2)
          }
        }

        logs.get.map(renderLog(_, 0).mkString("\n"))
      }
    }

    object Logger {
      def make: ZIO[Any, Nothing, Logger] =
        ZIO.scoped {
          FiberRef
            .make[Chunk[LogNode]](
              initial = Chunk.empty,
              fork = _ => Chunk.empty,
              join = (parent, child) => parent ++ Chunk(Child(child))
            )
            .map(Logger(_))
        }
    }
  }

  object AdvancedMain extends ZIOAppDefault {

    import AdvancedNestedLoggerImpl._

    def run =
      for {
        logger <- Logger.make
        _      <- logger.log("Starting application...")
        _ <- {
          for {
            _ <- logger.log("Initializing components...")
            // Small delay to show different timestamps
            _ <- ZIO.sleep(100.millis)
            _ <- logger.log("Configuring components...")
            _ <- ZIO.sleep(100.millis)
            _ <- logger.log("Setting up services...")
          } yield ()
        }.fork.flatMap(_.join)
        _   <- logger.log("Application started successfully.")
        log <- logger.render
        _   <- Console.printLine(log)
      } yield ()
  }

  /**
   *   6. Create a more advanced logging system that supports different log
   *      levels. It also should support regional settings for log levels so
   *      that the user can change the log level for a specific region of the
   *      application:
   *
   *     {{{
   *     trait Logger {
   *       def log(message: String): UIO[Unit]
   *       def withLogLevel[R, E, A](level: LogLevel)(
   *         zio: ZIO[R, E, A]
   *       ): ZIO[R, E, A]
   *     }
   *     }}}
   */

  import java.time.{Instant, ZoneId}
  import java.time.format.DateTimeFormatter

  object AdvancedLoggingSystemWithLogLevel {
    // Log levels with ordering
    sealed trait LogLevel extends Product with Serializable {
      def level: Int

      def name: String
    }

    object LogLevel {
      case object Debug extends LogLevel {
        val level = 0;
        val name  = "DEBUG"
      }

      case object Info extends LogLevel {
        val level = 1;
        val name  = "INFO"
      }

      case object Warn extends LogLevel {
        val level = 2;
        val name  = "WARN"
      }

      case object Error extends LogLevel {
        val level = 3;
        val name  = "ERROR"
      }

      implicit val ordering: Ordering[LogLevel] = Ordering.by(_.level)
    }

    // Enhanced log entry with log level
    case class LogEntry(
                         timestamp: Instant,
                         fiberId: String,
                         level: LogLevel,
                         message: String
                       )

    sealed trait LogNode
    case class Message(entry: LogEntry)       extends LogNode
    case class Child(entries: Chunk[LogNode]) extends LogNode

    case class Logger private (
                                private val logs: FiberRef[Chunk[LogNode]],
                                private val currentLevel: FiberRef[LogLevel]
                              ) {

      private def logWithLevel(level: LogLevel, message: String): UIO[Unit] =
        for {
          threshold <- currentLevel.get
          _ <- ZIO.when(level.level >= threshold.level) {
            for {
              timestamp <- Clock.instant
              fiberId   <- ZIO.fiberId.map(_.id).map(id => s"Fiber-$id")
              entry      = LogEntry(timestamp, fiberId, level, message)
              _         <- logs.update(_ :+ Message(entry))
            } yield ()
          }
        } yield ()

      def log(message: String): UIO[Unit] = info(message)

      def debug(message: String): UIO[Unit] =
        logWithLevel(LogLevel.Debug, message)

      def info(message: String): UIO[Unit] =
        logWithLevel(LogLevel.Info, message)

      def warn(message: String): UIO[Unit] =
        logWithLevel(LogLevel.Warn, message)

      def error(message: String): UIO[Unit] =
        logWithLevel(LogLevel.Error, message)

      def withLogLevel[R, E, A](level: LogLevel)(
        zio: ZIO[R, E, A]
      ): ZIO[R, E, A] =
        currentLevel.locally(level)(zio)

      def render: ZIO[Any, Nothing, String] = {
        val formatter = DateTimeFormatter
          .ofPattern("yyyy-MM-dd HH:mm:ss")
          .withZone(ZoneId.systemDefault())

        def renderLog(log: Chunk[LogNode], indent: Int): Chunk[String] = {
          val indentStr = " " * indent
          log.flatMap {
            case Message(LogEntry(timestamp, fiberId, level, message)) =>
              val timestampStr = formatter.format(timestamp)
              val levelStr     = f"${level.name}%-5s" // Left-aligned, 5 chars wide
              Chunk(
                s"${indentStr}[$timestampStr][$fiberId][$levelStr] $message"
              )
            case Child(childLog) =>
              renderLog(childLog, indent + 2)
          }
        }

        logs.get.map(renderLog(_, 0).mkString("\n"))
      }
    }

    object Logger {
      def make(
                defaultLevel: LogLevel = LogLevel.Info
              ): ZIO[Any, Nothing, Logger] =
        ZIO.scoped {
          for {
            logsRef <- FiberRef.make[Chunk[LogNode]](
              initial = Chunk.empty,
              fork = _ => Chunk.empty,
              join = (parent, child) => parent ++ Chunk(Child(child))
            )
            levelRef <- FiberRef.make(defaultLevel)
          } yield Logger(logsRef, levelRef)
        }
    }
  }

}
