package zionomicon.solutions

import zio._
import zio.stream._

package CombiningStreams {

  /**
   *   1. Implement a stream transformation that correlates events across
   *      multiple streams using flexible matching criteria:
   *
   * {{{
   * case class Event[A](
   *   id: String,
   *   timestamp: Long,
   *   data: A
   * )
   *
   * def correlateEvents[R, E, A, B, C](
   *   stream1: ZStream[R, E, Event[A]],
   *   stream2: ZStream[R, E, Event[B]],
   *   correlationWindow: Duration,
   *   matcher: (Event[A], Event[B]) => Boolean,
   *   combiner: (Event[A], Event[B]) => C
   * ): ZStream[R, E, C] = ???
   * }}}
   */
  package CorrelateEvents {

    case class Event[A](
      id: String,
      timestamp: Long,
      data: A
    )

    object Solution {

      def correlateEvents[R, E, A, B, C](
        stream1: ZStream[R, E, Event[A]],
        stream2: ZStream[R, E, Event[B]],
        correlationWindow: Duration,
        matcher: (Event[A], Event[B]) => Boolean,
        combiner: (Event[A], Event[B]) => C
      ): ZStream[R, E, C] = {
        case class State(
          bufferedLeft: Vector[Event[A]],
          pendingOutput: Vector[C]
        )

        def cleanupBuffer(
          currentTime: Long,
          buffer: Vector[Event[A]]
        ): Vector[Event[A]] =
          buffer.filter(_.timestamp >= currentTime - correlationWindow.toMillis)

        stream1.combine(stream2)(State(Vector(), Vector())) {
          (state, pullLeft, pullRight) =>
            if (state.pendingOutput.nonEmpty) {
              // Emit pending matches
              ZIO.succeed(
                Exit.succeed(
                  (
                    state.pendingOutput.head,
                    state.copy(pendingOutput = state.pendingOutput.tail)
                  )
                )
              )
            } else {
              // Pull from both streams
              (pullLeft.option <&> pullRight.option).flatMap {
                case (maybeLeft, maybeRight) =>
                  (maybeLeft, maybeRight) match {
                    case (Some(leftEvent), Some(rightEvent)) =>
                      // Both available: buffer left event and find matches with right
                      val buffered = state.bufferedLeft :+ leftEvent
                      val cleaned =
                        cleanupBuffer(rightEvent.timestamp, buffered)
                      val matches = cleaned
                        .filter(matcher(_, rightEvent))
                        .map(combiner(_, rightEvent))

                      if (matches.nonEmpty) {
                        val newState = state.copy(
                          bufferedLeft = cleaned,
                          pendingOutput =
                            if (matches.length > 1) matches.tail else Vector()
                        )
                        ZIO.succeed(Exit.succeed((matches.head, newState)))
                      } else {
                        ZIO.succeed(Exit.fail(None))
                      }

                    case (Some(_), None) =>
                      // Right stream done - no more correlations possible
                      ZIO.succeed(Exit.fail(None))

                    case (None, Some(rightEvent)) =>
                      // Left stream done - find matches with remaining buffer
                      val cleaned =
                        cleanupBuffer(rightEvent.timestamp, state.bufferedLeft)
                      val matches = cleaned
                        .filter(matcher(_, rightEvent))
                        .map(combiner(_, rightEvent))

                      if (matches.nonEmpty) {
                        val newState = state.copy(
                          bufferedLeft = cleaned,
                          pendingOutput =
                            if (matches.length > 1) matches.tail else Vector()
                        )
                        ZIO.succeed(Exit.succeed((matches.head, newState)))
                      } else {
                        ZIO.succeed(Exit.fail(None))
                      }

                    case (None, None) =>
                      // Both streams done
                      ZIO.succeed(Exit.fail(None))
                  }
              }
            }
        }
      }

    }

    // --- Example Showcase ---

    object Exercise1Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 1: Correlate Events ===")

        // Example 1: HTTP Request/Response Correlation
        _ <- Console.printLine(
               "\n--- Example 1: HTTP Request/Response Correlation ---"
             )
        _ <-
          Console.printLine(
            "Correlating HTTP requests with responses (matching by request ID, within 5s window)"
          )

        // Simulated HTTP requests with timestamps
        requests = ZStream(
                     Event("req-001", 1000L, "GET /api/users"),
                     Event("req-002", 1050L, "POST /api/data"),
                     Event("req-003", 1100L, "GET /api/config"),
                     Event("req-004", 1150L, "DELETE /api/cache")
                   )

        // Simulated HTTP responses arriving at different times
        responses = ZStream(
                      Event("req-001", 1250L, "200 OK"),
                      Event("req-002", 1500L, "201 Created"),
                      Event("req-003", 6200L, "200 OK"), // Outside 5s window!
                      Event("req-004", 6350L, "204 No Content")
                    )

        _ <- Solution
               .correlateEvents(
                 requests,
                 responses,
                 5.seconds,
                 (req: Event[String], resp: Event[String]) => req.id == resp.id,
                 (req: Event[String], resp: Event[String]) =>
                   s"${req.id}: '${req.data}' -> '${resp.data}' (${resp.timestamp - req.timestamp}ms)"
               )
               .foreach(c => Console.printLine(s"  ✓ $c"))
      } yield ()
    }
  }

  /**
   *   2. Combine two streams where the priority stream takes precedence. When
   *      elements are available from both streams, elements from the priority
   *      stream should be processed first:
   *
   * {{{
   * def priorityMerge[A](
   *   priority: ZStream[Any, Nothing, A],
   *   regular: ZStream[Any, Nothing, A]
   * ): ZStream[Any, Nothing, A] = ???
   * }}}
   */
  package PriorityMerge {

    object Solution {

      def priorityMerge[A](
        priority: ZStream[Any, Nothing, A],
        regular: ZStream[Any, Nothing, A]
      ): ZStream[Any, Nothing, A] = {
        case class State(priorityQueue: Vector[A], regularQueue: Vector[A])

        priority.combine(regular)(State(Vector(), Vector())) {
          (
            state: State,
            pullPriority: ZIO[Any, Option[Nothing], A],
            pullRegular: ZIO[Any, Option[Nothing], A]
          ) =>
            if (state.priorityQueue.nonEmpty) {
              // Priority: emit from priority queue first
              ZIO.succeed(
                Exit.succeed(
                  (
                    state.priorityQueue.head,
                    state.copy(priorityQueue = state.priorityQueue.tail)
                  )
                )
              )
            } else if (state.regularQueue.nonEmpty) {
              // Second priority: emit from regular queue
              ZIO.succeed(
                Exit.succeed(
                  (
                    state.regularQueue.head,
                    state.copy(regularQueue = state.regularQueue.tail)
                  )
                )
              )
            } else {
              // Both queues empty - pull from both streams
              (pullPriority.option <&> pullRegular.option).flatMap {
                case (maybePriority, maybeRegular) =>
                  (maybePriority, maybeRegular) match {
                    case (Some(priorityElem), Some(regularElem)) =>
                      // Both available - emit priority immediately, buffer regular for later
                      val newState =
                        state.copy(regularQueue = Vector(regularElem))
                      ZIO.succeed(Exit.succeed((priorityElem, newState)))

                    case (Some(priorityElem), None) =>
                      // Only priority available
                      ZIO.succeed(Exit.succeed((priorityElem, state)))

                    case (None, Some(regularElem)) =>
                      // Only regular available (priority stream exhausted)
                      ZIO.succeed(Exit.succeed((regularElem, state)))

                    case (None, None) =>
                      // Both streams exhausted
                      ZIO.succeed(Exit.fail(None))
                  }
              }
            }
        }
      }

    }

    // --- Example Showcase ---

    object Exercise2Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 2: Priority Merge ===")
        // Slow priority stream - emits every 5000ms
        slowPriority = ZStream(
                         "CRITICAL-1",
                         "CRITICAL-2",
                         "CRITICAL-3",
                         "CRITICAL-4"
                       ).schedule(Schedule.fixed(5000.millis))

        // Fast regular stream - emits every 100ms
        fastRegular = ZStream(
                        "task-a",
                        "task-b",
                        "task-c",
                        "task-d",
                        "task-e",
                        "task-f",
                        "task-g",
                        "task-h"
                      ).schedule(Schedule.fixed(100.millis))

        _ <- Solution
               .priorityMerge(slowPriority, fastRegular)
               .debug("emitted")
               .runDrain
      } yield ()
    }
  }

  /**
   *   3. Implement a stream combinator that dynamically adjusts sampling rates
   *      based on a control stream, useful for monitoring systems that need to
   *      adapt to the system load:
   *
   * {{{
   * case class SamplingConfig(samplesPerMinutes: Int)
   *
   * def adaptiveSampling[R, E, A](
   *   dataStream: ZStream[R, E, A],
   *   controlStream: ZStream[Any, Nothing, SamplingConfig]
   * ): ZStream[R, E, A] = ???
   * }}}
   */
  package AdaptiveSampling {

    case class SamplingConfig(samplesPerMinutes: Int)

    object Solution {

      def adaptiveSampling[R, E, A](
        dataStream: ZStream[R, E, A],
        controlStream: ZStream[Any, Nothing, SamplingConfig]
      ): ZStream[R, E, A] = {
        case class State(
          samplesPerMinute: Int,
          lastEmittedTime: Long, // Track when we last emitted (for sliding window)
          windowStartTime: Long
        )

        def shouldEmitSample(state: State, currentTime: Long): Boolean =
          if (state.lastEmittedTime == 0) {
            // First sample - always emit
            true
          } else {
            // Calculate minimum time between samples based on rate
            val oneMinuteMs = 60000L
            val msPerSample = oneMinuteMs / state.samplesPerMinute
            // Emit only if enough time has passed since last emission
            currentTime - state.lastEmittedTime >= msPerSample
          }

        // Pair data with latest config, then sample based on rate
        dataStream
          .zipLatestWith(controlStream) { (dataElem, config) =>
            (dataElem, config)
          }
          .scanZIO(
            (
              State(
                samplesPerMinute = 60,
                lastEmittedTime = 0,
                windowStartTime = 0
              ),
              Option.empty[A]
            )
          ) { (stateWithOutput, pair) =>
            val (state, _)         = stateWithOutput
            val (dataElem, config) = pair
            val currentTime        = java.lang.System.currentTimeMillis()
            // Initialize window time on first call
            val actualState =
              if (state.windowStartTime == 0)
                state.copy(windowStartTime = currentTime)
              else state
            // Update sampling rate
            val newState =
              actualState.copy(samplesPerMinute = config.samplesPerMinutes)
            // Check if we should emit based on sliding window (time-based spacing)
            val shouldEmit = shouldEmitSample(newState, currentTime)
            val updatedState =
              if (shouldEmit)
                newState.copy(lastEmittedTime = currentTime)
              else newState
            ZIO.succeed(
              (updatedState, if (shouldEmit) Some(dataElem) else None)
            )
          }
          .collect { case (_, Some(elem)) => elem }
      }

    }

    // --- Example Showcase ---
    object Exercise3Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 3: Adaptive Sampling ===")
        _ <-
          Console.printLine("\n--- Sampling with dynamic rate adjustment ---")

        // Data stream: simulates metrics being produced continuously
        dataStream = ZStream
                       .fromIterable(1 to 6000)
                       .map(i => s"metric-$i")
                       .schedule(
                         Schedule.spaced(10.millis)
                       )
        // Control stream: adjusts sampling rate based on system load
        controlStream =
          ZStream(
            SamplingConfig(samplesPerMinutes = 100),
            SamplingConfig(samplesPerMinutes = 200),
            SamplingConfig(samplesPerMinutes = 50)
          )
            .schedule(Schedule.fixed(5.seconds))
            .debug("Control stream emitted new sampling config")

        sampledCount <- Solution
                          .adaptiveSampling(dataStream, controlStream)
                          .debug("emitted")
                          .runCount

        _ <- Console.printLine(
               s"  Total data samples emitted: $sampledCount"
             )
        _ <- Console.printLine(
               s"  Sampling adjusted dynamically based on control stream"
             )
      } yield ()
    }
  }

}
