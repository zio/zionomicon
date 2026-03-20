package zionomicon.solutions

package Retries {

  /**
   *   1. Create a schedule that first attempts 3 quick retries with a delay of
   *      500 milliseconds. If those fail, switch to exponential backoff for 5
   *      more attempts, starting at 2 seconds and doubling each time.
   */
  package QuickRetriesWithExponentialBackoff {
    import zio._

    object QuickRetriesWithExponentialBackoffExample extends ZIOAppDefault {

      val quickRetries =
        Schedule.recurs(3) && Schedule.spaced(500.millis)

      val exponentialBackoff =
        Schedule.exponential(2.seconds) && Schedule.recurs(5)

      val schedule = quickRetries andThen exponentialBackoff

      val unreliableEffect: ZIO[Any, String, Unit] =
        ZIO.fail("Connection refused")

      val run: ZIO[Any, Any, Unit] =
        unreliableEffect
          .retry(schedule)
          .catchAll(e => Console.printLine(s"All retries exhausted: $e"))
    }
  }

  /**
   *   2. Create a schedule that only retries during "business hours" (9 AM to 5
   *      PM) on weekdays, with a one-hour delay between attempts.
   */
  package BusinessHoursSchedule {
    import zio._

    import java.time.DayOfWeek
    import java.time.OffsetDateTime

    object BusinessHoursScheduleExample extends ZIOAppDefault {

      def isBusinessHours(now: OffsetDateTime): Boolean = {
        val day  = now.getDayOfWeek
        val hour = now.getHour
        val isWeekday =
          day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
        isWeekday && hour >= 9 && hour < 17
      }

      val businessHoursOnly: Schedule[Any, Any, Unit] =
        Schedule.recurWhileZIO[Any, Any](_ =>
          Clock.currentDateTime.map(isBusinessHours)
        ).unit

      val schedule =
        Schedule.spaced(1.hour) && businessHoursOnly

      val unreliableEffect: ZIO[Any, String, Unit] =
        ZIO.fail("Service unavailable")

      val run: ZIO[Any, Any, Unit] =
        unreliableEffect
          .retry(schedule)
          .catchAll(e => Console.printLine(s"All retries exhausted: $e"))
    }
  }

  /**
   *   3. Create a progressive jittered schedule that delays between each
   *      recurrence and increases the jitter percentage by 5 percent as the
   *      number of retries increases.
   */
  package ProgressiveJitteredSchedule {
    import zio._

    object ProgressiveJitteredScheduleExample extends ZIOAppDefault {

      val baseDelay = 1.second

      /**
       * A schedule where jitter grows by 5% per retry.
       *
       * On retry N, the jitter percentage is N * 5%, so the actual delay is:
       *   baseDelay + random(0, baseDelay * N * 0.05)
       *
       * Retry 1: up to 5% jitter  (1s + 0–50ms)
       * Retry 2: up to 10% jitter (1s + 0–100ms)
       * Retry 3: up to 15% jitter (1s + 0–150ms)
       * ...
       */
      val attemptCounter: Schedule[Any, Any, Long] =
        Schedule.unfold(1L)(_ + 1L)

      val schedule: Schedule[Any, Any, Long] =
        (Schedule.spaced(baseDelay) *> attemptCounter).mapZIO { attempt =>
          val jitterFraction = attempt * 0.05
          val maxJitterMs =
            (baseDelay.toMillis * jitterFraction).toLong.max(1L)
          Random.nextLongBounded(maxJitterMs).flatMap { jitter =>
            val totalDelay = baseDelay + Duration.fromMillis(jitter)
            ZIO.debug(
              s"Retry #$attempt: jitter=${jitter}ms " +
                f"(max ${jitterFraction * 100}%.0f%% = ${maxJitterMs}ms), " +
                s"total delay=${totalDelay.toMillis}ms"
            ) *> Clock.sleep(Duration.fromMillis(jitter)).as(attempt)
          }
        }

      val unreliableEffect: ZIO[Any, String, Unit] =
        Clock.currentDateTime.flatMap(now =>
          ZIO.debug(s"[$now] Attempting API call...")
        ) *> ZIO.fail("Temporary error")

      val run: ZIO[Any, Any, Unit] =
        unreliableEffect
          .retry(schedule && Schedule.recurs(10))
          .catchAll(e => Console.printLine(s"All retries exhausted: $e"))
    }
  }

  /**
   *   4. We want to call an API that has a rate limit of 100 requests per hour.
   *      Create a schedule that respects this rate limit, only recursing 100
   *      times, and resets its retry count at the start of each hour.
   */
  package RateLimitSchedule {
    import zio._

    object RateLimitScheduleExample extends ZIOAppDefault {

      /**
       * A schedule that allows up to 100 recurrences per hour.
       *
       * - Schedule.recurs(100): limits to 100 retries
       * - .resetAfter(1.hour): resets the retry counter every hour,
       *   so a new window of 100 retries begins each hour
       *
       * The spacing of 36.seconds (3600s / 100) evenly distributes
       * requests across the hour to avoid bursting.
       */
      val schedule =
        (Schedule.recurs(100) && Schedule.spaced(36.seconds))
          .resetAfter(1.hour)

      val unreliableEffect: ZIO[Any, String, Unit] =
        Clock.currentDateTime.flatMap(now =>
          ZIO.debug(s"[$now] Making API call...")
        ) *> ZIO.fail("API error")

      val run: ZIO[Any, Any, Unit] =
        unreliableEffect
          .retry(schedule)
          .catchAll(e => Console.printLine(s"All retries exhausted: $e"))
    }
  }

  /**
   *   5. We have an API that, when we flood it with requests, starts to return
   *      the following error:
   *
   * {{{
   * case class RateLimitExceeded(
   *   retryAfter: Duration,
   *   remainingQuota: Int
   * )
   *
   * def apiCall: IO[RateLimitExceeded, Unit] = ???
   * }}}
   *
   * Write a schedule that retries the API call with respect to the
   * `retryAfter` duration so it doesn't perform any requests until the
   * `retryAfter` duration has elapsed.
   */
  package RetryAfterSchedule {
    import zio._

    /**
     * Error returned by a rate-limited API.
     *
     * @param retryAfter
     *   how long the client must wait before making another request. The
     *   server sets this based on when the rate-limit window resets.
     * @param remainingQuota
     *   how many requests the client has left in the current rate-limit
     *   window. When this reaches 0, subsequent requests will be rejected
     *   until the window resets.
     */
    case class RateLimitExceeded(
      retryAfter: Duration,
      remainingQuota: Int
    )

    object RetryAfterScheduleExample extends ZIOAppDefault {

      /**
       * A schedule that reads the retryAfter duration from the error and
       * uses it as the delay before the next attempt.
       *
       * - Schedule.identity extracts the error as the schedule's output
       * - addDelay uses the extracted error to compute a dynamic delay
       * - forever keeps retrying indefinitely (or until success)
       */
      val schedule: Schedule[Any, RateLimitExceeded, RateLimitExceeded] =
        Schedule.identity[RateLimitExceeded].addDelayZIO { error =>
          ZIO.debug(
            s"Rate limited: waiting ${error.retryAfter.toMillis}ms " +
              s"(remaining quota: ${error.remainingQuota})"
          ).as(error.retryAfter)
        }

      /**
       * Simulates an API with a quota of 100 requests. Each rejected call
       * consumes quota and the server asks us to wait longer as quota
       * depletes, then succeeds once the window resets on the 5th attempt.
       */
      val apiCall: ZIO[Ref[Int], RateLimitExceeded, Unit] =
        for {
          counter <- ZIO.service[Ref[Int]]
          count   <- counter.updateAndGet(_ + 1)
          now     <- Clock.currentDateTime
          _       <- ZIO.debug(s"[$now] API call attempt #$count")
          _ <-
            if (count <= 4)
              ZIO.fail(
                RateLimitExceeded(
                  retryAfter = Duration.fromMillis(count * 500L),
                  remainingQuota = 100 - (count * 25)
                )
              )
            else
              ZIO.debug(s"[$now] API call succeeded on attempt #$count!")
        } yield ()

      val run: ZIO[Any, Any, Unit] =
        apiCall
          .retry(schedule)
          .provideLayer(ZLayer(Ref.make(0)))
    }
  }

  /**
   *   6. Create a schedule for IoT devices that adjusts its polling frequency
   *      based on temperature changes:
   *
   *   - Poll every 5 minutes when the temperature is stable (change < 3°C in
   *     the last 5 minutes)
   *   - Poll every 1 second when the temperature is unstable (change >= 3°C)
   *   - Return to stable polling once the temperature stabilizes again
   */
  package IoTTemperaturePolling {
    import zio._

    object IoTTemperaturePollingExample extends ZIOAppDefault {

      val stableInterval   = 5.minutes
      val unstableInterval = 1.second
      val threshold        = 3.0

      /**
       * State carried between polls: the last temperature reading and
       * the delay to use before the next poll. We bundle both into a
       * case class because `addDelay` only sees the schedule's output,
       * not the internal fold computation — so the chosen interval must
       * be part of the output.
       */
      case class PollState(lastTemp: Option[Double], nextDelay: Duration)

      /**
       * Returns a schedule that adapts polling frequency based on
       * temperature stability.
       *
       * Key combinators:
       *
       *   - `Schedule.identity[Double]` passes each effect output
       *     (temperature reading) as the schedule's input, making it
       *     available to downstream combinators.
       *
       *   - `.foldZIO(init)(f)` accumulates state across recurrences.
       *     Here it tracks the last temperature to compute the delta.
       *     On each recurrence it classifies the reading as stable or
       *     unstable and stores the appropriate delay in `PollState`.
       *
       *   - `.addDelay(_.nextDelay)` reads the delay from the fold
       *     state, making the interval truly adaptive: each poll's
       *     delay is determined by the previous reading's stability.
       */
      def adaptivePollingSchedule(
        stableInterval: Duration,
        unstableInterval: Duration,
        threshold: Double
      ): Schedule[Any, Double, PollState] =
        Schedule.identity[Double]
          .foldZIO(PollState(None, Duration.Zero)) { (state, newTemp) =>
            val delta = state.lastTemp.fold(0.0)(prev =>
              Math.abs(newTemp - prev)
            )
            val isStable = delta < threshold
            val interval =
              if (isStable) stableInterval else unstableInterval
            ZIO.debug(
              f"  Temperature: $newTemp%.1f°C | " +
                f"Δ=$delta%.1f°C | " +
                s"${if (isStable) "STABLE" else "UNSTABLE"} → " +
                s"next poll in ${interval.render}"
            ).as(PollState(Some(newTemp), interval))
          }
          .addDelay(_.nextDelay)

      val schedule =
        adaptivePollingSchedule(stableInterval, unstableInterval, threshold)

      /**
       * Simulated temperature readings: stable at first, then a sudden
       * spike, then stabilizing again.
       */
      val readings = List(
        20.0, 20.5, 20.3, // stable
        24.0, 27.0,        // unstable (large jumps)
        27.2, 27.1, 27.0   // stable again
      )

      /**
       * For the demo we use shorter intervals so the output appears
       * quickly. In production, use the real stableInterval /
       * unstableInterval values.
       */
      val demoSchedule =
        adaptivePollingSchedule(
          stableInterval = 2.seconds,
          unstableInterval = 500.millis,
          threshold = threshold
        )

      val run: ZIO[Any, Any, Unit] =
        for {
          _   <- ZIO.debug("=== IoT Temperature Polling ===")
          ref <- Ref.make(readings)
          sensorReading = ref.modify {
                            case head :: tail => (head, tail)
                            case Nil          => (0.0, Nil)
                          }
          _ <- sensorReading.repeat(
                 demoSchedule && Schedule.recurs(readings.length - 1)
               )
        } yield ()
    }
  }

  /**
   *   7. Write a cron-like schedule that takes a set of seconds of the minute,
   *      minutes of the hour, hours of the day, and days of the week and
   *      returns a schedule that recurs at those times:
   *
   * {{{
   * def cronSchedule[Env, In](
   *   secondsOfMinute: Set[Int],
   *   minutesOfHours: Set[Int],
   *   hoursOfDay: Set[Int],
   *   daysOfWeek: Set[Int]
   * ): Schedule[Env, Int, Long] = ???
   * }}}
   */
  package CronSchedule {}

}
