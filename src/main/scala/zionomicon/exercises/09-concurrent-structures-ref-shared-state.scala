package zionomicon.exercises

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
  object CounterImpl {}

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
  object BoundedQueueImpl {}

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

  object CounterManagerImpl {}

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
  object NestedLoggerRendererImpl {}

  /**
   *   5. Change the log model and use a more detailed one instead of just a
   *      `String`, so that you can implement an advanced log renderer that adds
   *      timestamps and fiber IDs, like the following output:
   *
   *     ```scala
   *     [2024-01-01 10:00:01][fiber-1] Child foo
   *       [2024-01-01 10:00:02][fiber-2] Got 1
   *       [2024-01-01 10:00:03][fiber-2] Got 2
   *     [2024-01-01 10:00:01][fiber-1] Child bar
   *       [2024-01-01 10:00:02][fiber-3] Got 3
   *       [2024-01-01 10:00:03][fiber-3] Got 4
   *     ```
   *
   * Hint: You can use the following model for the log entry:
   *
   *     ```scala
   *     case class LogEntry(
   *       timestamp: java.time.Instant,
   *       fiberId: String,
   *       message: String
   *     )
   *     ```
   */
  object AdvancedNestedLoggerImpl {}

  /**
   *   6. Create a more advanced logging system that supports different log
   *      levels. It also should support regional settings for log levels so
   *      that the user can change the log level for a specific region of the
   *      application:
   *
   *     ```scala
   *     trait Logger {
   *       def log(message: String): UIO[Unit]
   *       def withLogLevel[R, E, A](level: LogLevel)(
   *         zio: ZIO[R, E, A]
   *       ): ZIO[R, E, A]
   *     }
   *     ```
   */
  object AdvancedLoggingSystemWithLogLevel {}

}
