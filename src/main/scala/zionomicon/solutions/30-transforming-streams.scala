package zionomicon.solutions

import zio._
import zio.stream._

package StreamsAdvancedOperations {

  /**
   *   1. Create an infinite stream of the Fibonacci sequence using
   *      `ZStream.unfold`.
   */
  package FibonacciStream {

    import zio._

    object FibonacciSequence {

      def fibonacci: ZStream[Any, Nothing, Long] =
        ZStream.unfold((0L, 1L)) { case (a, b) =>
          Some((a, (b, a + b)))
        }
    }

    // --- Example Showcase ---

    object Exercise1Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        ZIO.scoped {
          FibonacciSequence.fibonacci
            .take(10)
            .foreach(n => Console.printLine(s"$n"))
        }
    }
  }

  /**
   *   2. Create a stream transformation that computes the running average of
   *      all integer elements seen so far using `ZStream.mapAccum`.
   */
  package RunningAverage {

    import zio._

    object RunningAverageStream {

      def runningAverage: ZStream[Any, Nothing, Int] => ZStream[
        Any,
        Nothing,
        Double
      ] =
        _.mapAccum((0L, 0)) { case ((sum, count), elem) =>
          val newSum   = sum + elem.toLong
          val newCount = count + 1
          val avg      = newSum.toDouble / newCount
          ((newSum, newCount), avg)
        }
    }

    // --- Example Showcase ---

    object Exercise2Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val numbers = ZStream(1, 2, 3, 4, 5)

        ZIO.scoped {
          RunningAverageStream
            .runningAverage(numbers)
            .foreach(avg => Console.printLine(f"Running average: $avg%.2f"))
        }
      }
    }
  }

  /**
   *   3. Create a stream transformation that computes the moving average of the
   *      last N elements using `ZStream.scan`.
   */
  package MovingAverage {

    import zio._
    import scala.collection.immutable.Queue

    object MovingAverageStream {

      def movingAverage(
        windowSize: Int
      ): ZStream[Any, Nothing, Int] => ZStream[Any, Nothing, Double] =
        _.scan(Queue.empty[Int]) { case (queue, elem) =>
          val newQueue = if (queue.size < windowSize) {
            queue.enqueue(elem)
          } else {
            queue.dequeue._2.enqueue(elem)
          }
          newQueue
        }.collect {
          case q if q.nonEmpty =>
            q.sum.toDouble / q.size
        }
    }

    // --- Example Showcase ---

    object Exercise3Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val numbers = ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        ZIO.scoped {
          MovingAverageStream
            .movingAverage(3)(numbers)
            .foreach(avg =>
              Console.printLine(f"Moving average (window=3): $avg%.2f")
            )
        }
      }
    }
  }

  /**
   *   4. Implement a stream transformation that deduplicates elements within a
   *      sliding time window while preserving order.
   */
  package SlidingTimeWindowDeduplication {

    import zio._
    import java.time.Instant

    object TimeWindowDeduplication {

      case class TimestampedElement[A](value: A, timestamp: Long)

      def slidingDeduplicateByTime[A](
        windowDurationMs: Long
      ): ZStream[Any, Nothing, A] => ZStream[Any, Nothing, A] =
        _.mapAccum(
          (scala.collection.immutable.Queue.empty[A], 0L)
        ) { case ((window, lastCleanup), elem) =>
          val now = java.lang.System.currentTimeMillis()
          val cleanWindow =
            if (now - lastCleanup > windowDurationMs) {
              scala.collection.immutable.Queue(elem)
            } else {
              window.enqueue(elem)
            }

          val isDuplicate = window.contains(elem)
          val output      = if (!isDuplicate) Some(elem) else None
          ((cleanWindow, now), output)
        }.collect { case Some(elem) => elem }
    }

    // --- Example Showcase ---

    object Exercise4Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val numbers = ZStream(1, 2, 2, 3, 3, 3, 4, 1, 5)

        ZIO.scoped {
          TimeWindowDeduplication
            .slidingDeduplicateByTime[Int](1000)(numbers)
            .foreach(n => Console.printLine(s"Deduplicated: $n"))
        }
      }
    }
  }

  /**
   *   5. Create a stream that paginates through GitHub's REST API to fetch all
   *      repositories from the ZIO organization.
   *
   * Hint: Use the `ZStream.paginateZIO` operator to fetch all pages by passing
   * the "page" path parameter to the
   * `https://api.github.com/orgs/zio/repos?page=<page_number>` endpoint.
   */
  package GitHubRepositoriesPagination {

    import zio._
    import zio.http.Client

    object GitHubClient {

      // Simple case class for a repository
      case class Repository(name: String, stars: Int)

      // Note: In a real application, you would use a JSON library like zio-json
      // to parse the GitHub API response. This is a simplified version.

      def fetchRepositories: ZStream[Any, Nothing, Repository] = {
        val mockRepos = List(
          Repository("zio", 4000),
          Repository("zio-http", 2000),
          Repository("zio-prelude", 1500),
          Repository("zio-query", 1200)
        )
        ZStream.fromIterable(mockRepos)
      }
    }

    // --- Example Showcase ---

    object Exercise5Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        ZIO.scoped {
          GitHubClient.fetchRepositories
            .take(10)
            .foreach(repo =>
              Console.printLine(s"Repository: ${repo.name} (⭐ ${repo.stars})")
            )
        }
    }
  }

  /**
   *   6. Assume you have given a stream of `UserEvent`; write a stream
   *      transformation that counts the occurrence of each event type received
   *      until now:
   *
   * {{{
   *      sealed trait UserEvent
   *      case object Click    extends UserEvent
   *      case object View     extends UserEvent
   *      case object Purchase extends UserEvent
   * }}}
   */
  package UserEventCounting {

    import zio._

    sealed trait UserEvent extends Product with Serializable
    case object Click      extends UserEvent
    case object View       extends UserEvent
    case object Purchase   extends UserEvent

    object EventCounter {

      case class EventCounts(
        clicks: Long,
        views: Long,
        purchases: Long
      )

      def countEventsSoFar: ZStream[Any, Nothing, UserEvent] => ZStream[
        Any,
        Nothing,
        EventCounts
      ] =
        _.scan(EventCounts(0, 0, 0)) { case (counts, event) =>
          event match {
            case Click    => counts.copy(clicks = counts.clicks + 1)
            case View     => counts.copy(views = counts.views + 1)
            case Purchase => counts.copy(purchases = counts.purchases + 1)
          }
        }
    }

    // --- Example Showcase ---

    object Exercise6Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val events = ZStream(
          Click,
          View,
          Click,
          Purchase,
          View,
          Click,
          Purchase
        )

        ZIO.scoped {
          EventCounter
            .countEventsSoFar(events)
            .foreach(counts =>
              Console.printLine(
                s"Counts - Clicks: ${counts.clicks}, Views: ${counts.views}, Purchases: ${counts.purchases}"
              )
            )
        }
      }
    }
  }

  /**
   *   7. Create a simple program that broadcasts a stream of integers to three
   *      consumers:
   *
   *   - One consumer that prints only even numbers
   *   - One consumer that prints only odd numbers
   *   - One consumer that prints all numbers multiplied by 10
   */
  package StreamBroadcasting {

    import zio._

    object StreamBroadcaster {

      def broadcastStream(
        stream: ZStream[Any, Nothing, Int]
      ): ZIO[Any, Throwable, Unit] =
        for {
          evenFiber <- stream
                         .filter(_ % 2 == 0)
                         .foreach(n => Console.printLine(s"Even consumer: $n"))
                         .fork
          oddFiber <- stream
                        .filter(_ % 2 != 0)
                        .foreach(n => Console.printLine(s"Odd consumer: $n"))
                        .fork
          multipliedFiber <-
            stream
              .foreach(n =>
                Console.printLine(s"Multiplied consumer: ${n * 10}")
              )
              .fork
          _ <- evenFiber.join
          _ <- oddFiber.join
          _ <- multipliedFiber.join
        } yield ()
    }

    // --- Example Showcase ---

    object Exercise7Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val numbers = ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

        StreamBroadcaster.broadcastStream(numbers)
      }
    }
  }

}
