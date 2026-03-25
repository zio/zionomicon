package zionomicon.solutions

package StreamsAdvancedOperations {

  /**
   *   1. Create an infinite stream of the Fibonacci sequence using
   *      `ZStream.unfold`.
   */
  package FibonacciStream {

    import zio._
    import zio.stream._

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
    import zio.stream._

    object RunningAverageStream {

      def runningAverage: ZStream[Any, Nothing, Int] => ZStream[
        Any,
        Nothing,
        Double
      ] =
        _.mapAccum((0L, 0)) { case ((sum, count), elem) =>
          val newSum   = sum + elem
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
    import zio.stream._
    import scala.collection.immutable.Queue

    object MovingAverageStream {

      def movingAverage(
        windowSize: Int
      ): ZStream[Any, Nothing, Int] => ZStream[Any, Nothing, Double] = {
        require(
          windowSize > 0,
          s"windowSize must be positive, but was $windowSize"
        )
        (stream: ZStream[Any, Nothing, Int]) =>
          stream
            .scan(Queue.empty[Int]) { case (queue, elem) =>
              val newQueue = if (queue.size < windowSize) {
                queue.enqueue(elem)
              } else {
                queue.dequeue._2.enqueue(elem)
              }
              newQueue
            }
            .collect {
              case q if q.nonEmpty =>
                q.sum.toDouble / q.size
            }
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
    import zio.stream._

    object TimeWindowDeduplication {

      case class TimestampedElement[A](value: A, timestamp: zio.Duration)

      def slidingDeduplicateByTime[A](
        windowDuration: zio.Duration
      ): ZStream[Any, Nothing, A] => ZStream[Any, Nothing, A] =
        _.mapAccumZIO(
          (
            scala.collection.immutable.Queue.empty[TimestampedElement[A]],
            Set.empty[A]
          )
        ) { case ((window, currentSet), elem) =>
          Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).map {
            nowMs =>
              val now      = nowMs.milliseconds
              val windowMs = windowDuration.toMillis

              // Evict elements that are older than the window duration
              @annotation.tailrec
              def evictExpired(
                q: scala.collection.immutable.Queue[TimestampedElement[A]],
                s: Set[A]
              ): (
                scala.collection.immutable.Queue[TimestampedElement[A]],
                Set[A]
              ) =
                q.headOption match {
                  case Some(TimestampedElement(value, ts))
                      if (nowMs - ts.toMillis) > windowMs =>
                    evictExpired(q.tail, s - value)
                  case _ =>
                    (q, s)
                }

              val (cleanWindow, cleanSet) = evictExpired(window, currentSet)

              if (cleanSet.contains(elem)) {
                // Element is a duplicate within the current time window
                // false indicates we won't emit this element because it's a duplicate
                ((cleanWindow, cleanSet), (false, elem))
              } else {
                // New element within the window; add it to the state
                val updatedWindow =
                  cleanWindow.enqueue(TimestampedElement(elem, now))
                val updatedSet = cleanSet + elem
                // true indicates we will emit this element because it's not a duplicate
                ((updatedWindow, updatedSet), (true, elem))
              }
          }
        }.collect { case (true, elem) => elem }
    }

    // --- Example Showcase ---

    object Exercise4Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = {
        val numbers = ZStream(1, 2, 2, 2, 2, 3, 2, 4, 1).schedule(
          Schedule.spaced(500.milliseconds)
        )

        TimeWindowDeduplication
          .slidingDeduplicateByTime[Int](3.seconds)(numbers)
          .debug("Deduplicated element")
          .runDrain
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
    import zio.stream._
    import zio.json._

    object GitHubClient {

      // Case classes for JSON decoding
      case class Repository(name: String, stargazers_count: Int)

      object Repository {
        implicit val decoder: JsonDecoder[Repository] =
          DeriveJsonDecoder.gen[Repository]
      }

      private def fetchPage(
        org: String,
        page: Int
      ): ZIO[Any, Throwable, List[Repository]] =
        ZIO.attempt {
          val url =
            s"https://api.github.com/orgs/$org/repos?page=$page&per_page=30&sort=stars&order=desc"
          try {
            val connection = new java.net.URL(url).openConnection()
            connection
              .asInstanceOf[java.net.HttpURLConnection]
              .setRequestProperty(
                "User-Agent",
                "Scala-ZIO-Client"
              )
            connection.setConnectTimeout(5000)
            connection.setReadTimeout(10000)

            val responseCode =
              connection
                .asInstanceOf[java.net.HttpURLConnection]
                .getResponseCode()
            if (responseCode != 200) {
              throw new RuntimeException(
                s"HTTP $responseCode: ${connection.asInstanceOf[java.net.HttpURLConnection].getResponseMessage()}"
              )
            }

            val source = scala.io.Source.fromInputStream(
              connection.getInputStream
            )
            try {
              val body = source.mkString
              if (body.isEmpty) {
                throw new RuntimeException(
                  "Empty response body from GitHub API"
                )
              }
              body
                .fromJson[List[Repository]]
                .fold(
                  err =>
                    throw new RuntimeException(
                      s"JSON decode error: $err. Response: ${body.take(200)}"
                    ),
                  repos => repos
                )
            } finally source.close()
          } catch {
            case e: java.net.UnknownHostException =>
              throw new RuntimeException(
                s"DNS resolution failed for api.github.com. Check your network/proxy settings: ${e.getMessage}",
                e
              )
            case e: java.net.ConnectException =>
              throw new RuntimeException(
                s"Failed to connect to api.github.com. Check proxy settings and network connectivity: ${e.getMessage}",
                e
              )
            case e: javax.net.ssl.SSLException =>
              throw new RuntimeException(
                s"SSL/TLS certificate error: ${e.getMessage}. Try setting: -Dcom.sun.security.cert.checkRevocation=false",
                e
              )
            case e: Throwable =>
              throw new RuntimeException(
                s"Error fetching from GitHub API: ${e.getClass.getSimpleName}: ${e.getMessage}",
                e
              )
          }
        }

      def fetchRepositories(
        org: String = "zio"
      ): ZStream[Any, Throwable, Repository] =
        ZStream
          .paginateZIO(1) { page =>
            fetchPage(org, page).map { repos =>
              val nextPage =
                if (repos.isEmpty || repos.size < 30) None
                else Some(page + 1)
              (repos, nextPage)
            }
          }
          .flatMap(repos => ZStream(repos: _*))
    }

    // --- Example Showcase ---

    object Exercise5Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        (for {
          // Collect repositories from the stream and sort by stars
          repos <- GitHubClient
                     .fetchRepositories()
                     .runCollect
          sorted = repos.sortBy(_.stargazers_count)(Ordering[Int].reverse)
          topTen = sorted.take(10)
          _     <- Console.printLine("=== Top 10 ZIO Repositories by Stars ===")
          _ <-
            ZIO.foreach(topTen.zipWithIndex) { case (repo, idx) =>
              Console.printLine(
                s"${idx + 1}. ${repo.name.padTo(30, ' ')} ⭐ ${repo.stargazers_count}"
              )
            }
        } yield ()).catchAll { err =>
          val errorMsg = s"Error fetching repositories: ${err.getMessage}"
          val stackTrace =
            if (err.getCause != null)
              s"\nCause: ${err.getCause.getClass.getSimpleName}: ${err.getCause.getMessage}"
            else ""
          Console.printLineError(errorMsg + stackTrace)
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
    import zio.stream._

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
        }.drop(1)
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
    import zio.stream._

    object StreamBroadcaster {

      def broadcastStream(
        stream: ZStream[Any, Nothing, Int]
      ): ZIO[Any, Nothing, Unit] =
        ZIO.scoped {
          for {
            // Create three broadcasted streams from a single upstream producer
            streams         <- stream.broadcast(3, 16)
            evenStream       = streams(0)
            oddStream        = streams(1)
            multipliedStream = streams(2)
            // Run all consumers in parallel using zipParRight for fan-out
            _ <- evenStream
                   .filter(_ % 2 == 0)
                   .foreach(n => Console.printLine(s"Even consumer: $n"))
                   .mapError(_ => ())
                   .zipParRight(
                     oddStream
                       .filter(_ % 2 != 0)
                       .foreach(n => Console.printLine(s"Odd consumer: $n"))
                       .mapError(_ => ())
                   )
                   .zipParRight(
                     multipliedStream
                       .foreach(n =>
                         Console.printLine(s"Multiplied consumer: ${n * 10}")
                       )
                       .mapError(_ => ())
                   )
          } yield ()
        }
          .catchAll(_ => ZIO.unit)
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
