package zionomicon.solutions

import zio._
import zio.stream._
import java.time.Instant
import scala.math.Ordering

package StreamingPipelines {

  /**
   *   1. Create a pipeline that groups consecutive elements into pairs:
   *      - Option A: Overlapping pairs (1,2,3,4 → (1,2), (2,3), (3,4))
   *      - Option B: Non-overlapping pairs (1,2,3,4 → (1,2), (3,4))
   *
   * {{{
   * def pair[A]: ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   * }}}
   */
  package PairPipeline {

    object Solution {

      /**
       * Option A: Overlapping pairs - each element pairs with the next one
       * Example: 1,2,3,4 → (1,2), (2,3), (3,4)
       * Useful for: detecting changes/trends between consecutive elements
       */
      def pairOverlapping[A]: ZPipeline[Any, Nothing, A, (A, A)] = {

        def channel(previous: Option[A]): ZChannel[Any, ZNothing, Chunk[A], Any, ZNothing, Chunk[(A, A)], Any] =
          ZChannel.readWithCause(
            elem => {
              val pairs = scala.collection.mutable.ListBuffer.empty[(A, A)]
              var currentPrev = previous

              elem.foreach { current =>
                currentPrev.foreach { prev =>
                  pairs += ((prev, current))
                }
                currentPrev = Some(current)
              }

              if (pairs.nonEmpty)
                ZChannel.write(Chunk.fromIterable(pairs)) *> channel(currentPrev)
              else
                channel(currentPrev)
            },
            err => ZChannel.failCause(err),
            done => ZChannel.succeed(done)
          )

        ZPipeline.fromChannel(channel(None))
      }

      /**
       * Option B: Non-overlapping pairs - consecutive elements are grouped into disjoint pairs
       * Example: 1,2,3,4 → (1,2), (3,4)
       * Useful for: batch processing, fixed-size chunking
       */
      def pairNonOverlapping[A]: ZPipeline[Any, Nothing, A, (A, A)] = {

        def channel(pending: Option[A]): ZChannel[Any, ZNothing, Chunk[A], Any, ZNothing, Chunk[(A, A)], Any] =
          ZChannel.readWithCause(
            elem => {
              val pairs = scala.collection.mutable.ListBuffer.empty[(A, A)]
              var current = pending

              elem.foreach { newElem =>
                current match {
                  case Some(first) =>
                    // We have a pending element, pair it with this new element
                    pairs += ((first, newElem))
                    current = None
                  case None =>
                    // No pending element, make this one pending
                    current = Some(newElem)
                }
              }

              if (pairs.nonEmpty)
                ZChannel.write(Chunk.fromIterable(pairs)) *> channel(current)
              else
                channel(current)
            },
            err => {
              // On error, discard any pending unpaired element
              ZChannel.failCause(err)
            },
            done => {
              // On stream end, discard any unpaired element (can't form a complete pair)
              ZChannel.succeed(done)
            }
          )

        ZPipeline.fromChannel(channel(None))
      }

      // Alias for the most common interpretation (overlapping)
      def pair[A]: ZPipeline[Any, Nothing, A, (A, A)] = pairOverlapping[A]

    }

    // --- Example Showcase ---

    object Exercise1Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 1: Pair Pipeline ===")

        // Example 1A: Overlapping pairs
        _ <- Console.printLine("\n--- Option A: Overlapping Pairs ---")
        _ <- Console.printLine(
               "Each element pairs with the next: 1,2,3,4,5 → (1,2), (2,3), (3,4), (4,5)"
             )
        _ <- Console.printLine("Use case: Detecting changes/trends between consecutive elements\n")

        _ <- ZStream(1, 2, 3, 4, 5)
               .via(Solution.pairOverlapping[Int])
               .foreach(pair => Console.printLine(s"  ${pair._1} -> ${pair._2}"))

        // Example 1B: Non-overlapping pairs
        _ <- Console.printLine("\n--- Option B: Non-Overlapping Pairs ---")
        _ <- Console.printLine(
               "Disjoint pairs: 1,2,3,4,5 → (1,2), (3,4) [5 discarded, unpaired]"
             )
        _ <- Console.printLine("Use case: Batch processing, fixed-size chunking\n")

        _ <- ZStream(1, 2, 3, 4, 5)
               .via(Solution.pairNonOverlapping[Int])
               .foreach(pair => Console.printLine(s"  (${pair._1}, ${pair._2})"))

        // Example 2: String pairing with overlapping
        _ <- Console.printLine("\n--- Example: String Overlapping Pairing ---")
        _ <- Console.printLine(
               "Characters: a,b,c,d,e\n"
             )

        _ <- ZStream("a", "b", "c", "d", "e")
               .via(Solution.pairOverlapping[String])
               .foreach(pair =>
                 Console.printLine(s"  (${pair._1}, ${pair._2})")
               )

        // Example 3: Edge Cases
        _ <- Console.printLine("\n--- Edge Cases ---")
        _ <- Console.printLine("Empty stream with overlapping:")

        emptyCountOverlap <- ZStream.empty
                               .via(Solution.pairOverlapping[Int])
                               .runCount
        _ <- Console.printLine(s"  Result: $emptyCountOverlap pairs")

        _ <- Console.printLine("\nSingle element with overlapping:")

        singleCountOverlap <- ZStream(42)
                                .via(Solution.pairOverlapping[Int])
                                .runCount
        _ <- Console.printLine(s"  Result: $singleCountOverlap pairs")

        _ <- Console.printLine("\nTwo elements with non-overlapping:")

        twoCountNonOverlap <- ZStream(1, 2)
                                .via(Solution.pairNonOverlapping[Int])
                                .runCount
        _ <- Console.printLine(s"  Result: $twoCountNonOverlap pairs")

        _ <- Console.printLine("\nThree elements with non-overlapping:")

        threeCountNonOverlap <- ZStream(1, 2, 3)
                                  .via(Solution.pairNonOverlapping[Int])
                                  .runCount
        _ <- Console.printLine(s"  Result: $threeCountNonOverlap pairs (3rd element unpaired)")
      } yield ()
    }

  }

  /**
   *   2. Design a pipeline that outputs the minimum and maximum values from
   *      a continuous data stream within a fixed time window.
   *
   *      Option A: Tumbling Windows (non-overlapping)
   *        - Each window is separate and non-overlapping
   *        - Window 1: [0-500ms), Window 2: [500-1000ms), etc.
   *        - Example: [1,2,3,4,5,6,7] → (1,3), (4,6), (7,7)
   *        - Use case: Hourly/daily aggregations, clear boundaries
   *
   *      Option B: Sliding Windows (overlapping)
   *        - Windows overlap continuously as time progresses
   *        - Emit every interval with elements from last windowSize duration
   *        - Example: [1,2,3,4,5,6,7] → (1,3), (2,4), (3,5), (4,6), (5,7)
   *        - Use case: Real-time monitoring, continuous dashboards
   *
   * {{{
   * def minMaxWindowTumbling[A: Ordering](
   *   windowSize: Duration
   * ): ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   *
   * def minMaxWindowSliding[A: Ordering](
   *   windowSize: Duration,
   *   emitInterval: Duration
   * ): ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   * }}}
   */
  package MinMaxWindow {

    case class TimestampedValue[A](timestamp: Long, value: A)

    object Solution {

      /**
       * Option A: Tumbling (non-overlapping) windows
       * Windows are separate: [0-500), [500-1000), [1000-1500)
       * Clear buffer at each window boundary
       */
      def minMaxWindowTumbling[A: Ordering](
        windowSize: Duration
      ): ZPipeline[Any, Nothing, A, (A, A)] = {

        def channel(
          buffer: Vector[A],
          lastEmitTime: Long
        ): ZChannel[Any, ZNothing, Chunk[A], Any, ZNothing, Chunk[(A, A)], Any] =
          ZChannel.readWithCause(
            elem => {
              val newBuffer = buffer ++ elem
              val currentTime = java.lang.System.currentTimeMillis()

              if (currentTime - lastEmitTime >= windowSize.toMillis && newBuffer.nonEmpty) {
                val min = newBuffer.min
                val max = newBuffer.max
                ZChannel.write(Chunk((min, max))) *> channel(Vector.empty, currentTime)
              } else {
                channel(newBuffer, lastEmitTime)
              }
            },
            err => ZChannel.failCause(err),
            done => {
              // Emit final window if buffer is not empty
              if (buffer.nonEmpty) {
                val min = buffer.min
                val max = buffer.max
                ZChannel.write(Chunk((min, max))) *> ZChannel.succeed(done)
              } else {
                ZChannel.succeed(done)
              }
            }
          )

        ZPipeline.fromChannel(channel(Vector.empty, Long.MinValue))
      }

      /**
       * Option B: Sliding (overlapping) windows
       * Windows overlap: [0-500), [200-700), [400-900), etc.
       * Keep all elements with timestamps, filter aged-out elements
       */
      def minMaxWindowSliding[A: Ordering](
        windowSize: Duration,
        emitInterval: Duration
      ): ZPipeline[Any, Nothing, A, (A, A)] = {

        def channel(
          buffer: Vector[TimestampedValue[A]],
          lastEmitTime: Long
        ): ZChannel[Any, ZNothing, Chunk[A], Any, ZNothing, Chunk[(A, A)], Any] =
          ZChannel.readWithCause(
            elem => {
              // Add new elements with timestamps
              val currentTime = java.lang.System.currentTimeMillis()
              val timestampedElems = elem.map(TimestampedValue(currentTime, _))
              val newBuffer = buffer ++ timestampedElems

              // Remove elements older than the window
              val windowStart = currentTime - windowSize.toMillis
              val filteredBuffer = newBuffer.filter(_.timestamp >= windowStart)

              if (currentTime - lastEmitTime >= emitInterval.toMillis && filteredBuffer.nonEmpty) {
                val min = filteredBuffer.map(_.value).min
                val max = filteredBuffer.map(_.value).max
                ZChannel.write(Chunk((min, max))) *>
                  channel(filteredBuffer, currentTime)
              } else {
                channel(filteredBuffer, lastEmitTime)
              }
            },
            err => ZChannel.failCause(err),
            done => {
              // Emit final window if buffer is not empty
              val currentTime = java.lang.System.currentTimeMillis()
              val windowStart = currentTime - windowSize.toMillis
              val filteredBuffer = buffer.filter(_.timestamp >= windowStart)

              if (filteredBuffer.nonEmpty) {
                val min = filteredBuffer.map(_.value).min
                val max = filteredBuffer.map(_.value).max
                ZChannel.write(Chunk((min, max))) *> ZChannel.succeed(done)
              } else {
                ZChannel.succeed(done)
              }
            }
          )

        ZPipeline.fromChannel(channel(Vector.empty, Long.MinValue))
      }

    }

    // --- Example Showcase ---

    object Exercise2Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 2: Min-Max Window Pipeline ===")

        // Example 1A: Tumbling (non-overlapping) windows
        _ <- Console.printLine(
               "\n--- Option A: Tumbling Windows (500ms) ---"
             )
        _ <- Console.printLine(
               "Non-overlapping windows: [0-500), [500-1000), etc."
             )
        _ <- Console.printLine(
               "Stream: [5, 2, 8, 1, 9, 3, 7] at 100ms intervals\n"
             )

        _ <- ZStream(5, 2, 8, 1, 9, 3, 7)
               .schedule(Schedule.spaced(100.millis))
               .via(Solution.minMaxWindowTumbling[Int](500.millis))
               .foreach(minMax =>
                 Console.printLine(
                   s"  Window: min=${minMax._1}, max=${minMax._2}"
                 )
               )

        // Example 1B: Sliding (overlapping) windows
        _ <- Console.printLine(
               "\n--- Option B: Sliding Windows (500ms window, emit every 200ms) ---"
             )
        _ <- Console.printLine(
               "Overlapping windows: keeps recent 500ms of data"
             )
        _ <- Console.printLine(
               "Stream: [5, 2, 8, 1, 9, 3, 7] at 100ms intervals\n"
             )

        _ <- ZStream(5, 2, 8, 1, 9, 3, 7)
               .schedule(Schedule.spaced(100.millis))
               .via(Solution.minMaxWindowSliding[Int](500.millis, 200.millis))
               .foreach(minMax =>
                 Console.printLine(
                   s"  Window: min=${minMax._1}, max=${minMax._2}"
                 )
               )

        // Example 2: Comparing both with simple sequence
        _ <- Console.printLine(
               "\n--- Comparison: Both on [1, 2, 3, 4, 5, 6, 7, 8, 9, 10] ---"
             )
        _ <- Console.printLine("Window: 400ms, Elements: 100ms apart\n")

        _ <- Console.printLine("Tumbling (clear on boundary):")
        _ <- ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
               .schedule(Schedule.spaced(100.millis))
               .via(Solution.minMaxWindowTumbling[Int](400.millis))
               .foreach(minMax =>
                 Console.printLine(s"  (${minMax._1}, ${minMax._2})")
               )

        _ <- Console.printLine("\nSliding (keep recent 400ms, emit every 150ms):")
        _ <- ZStream(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
               .schedule(Schedule.spaced(100.millis))
               .via(Solution.minMaxWindowSliding[Int](400.millis, 150.millis))
               .foreach(minMax =>
                 Console.printLine(s"  (${minMax._1}, ${minMax._2})")
               )

        // Example 3: Real-world use case - temperature monitoring
        _ <- Console.printLine(
               "\n--- Use Case: Temperature Monitoring (simulated) ---"
             )
        _ <- Console.printLine("Tumbling: Hourly temperature ranges\n")

        _ <- ZStream(22, 21, 23, 24, 25, 23, 22, 21, 20, 19)
               .schedule(Schedule.spaced(50.millis)) // Simulating 6-min intervals
               .via(Solution.minMaxWindowTumbling[Int](300.millis)) // 300ms = simulated hour
               .foreach(minMax =>
                 Console.printLine(
                   s"  Hourly range: ${minMax._1}°C - ${minMax._2}°C"
                 )
               )
      } yield ()
    }

  }

  /**
   *   3. Implement a stream sessionization pipeline that groups events into
   *      sessions based on an inactivity gap between events. A session is
   *      considered ended if there is no event for a given time window.
   *
   * {{{
   * import java.time.Instant
   *
   * case class UserEvent(
   *   userId: String,
   *   timestamp: Instant,
   * )
   *
   * case class Session(
   *   sessionId: String,
   *   userId: String,
   *   startTime: Instant,
   *   endTime: Instant,
   *   duration: Duration,
   *   events: List[UserEvent]
   * )
   *
   * def sessionize(
   *   gapThreshold: Duration
   * ): ZPipeline[Any, Nothing, UserEvent, Session] =
   *   ???
   * }}}
   */
  package Sessionize {

    case class UserEvent(
      userId: String,
      timestamp: Instant
    )

    case class Session(
      sessionId: String,
      userId: String,
      startTime: Instant,
      endTime: Instant,
      duration: Duration,
      events: List[UserEvent]
    )

    object Solution {

      def sessionize(
        gapThreshold: Duration
      ): ZPipeline[Any, Nothing, UserEvent, Session] = {

        case class SessionState(
          sessionId: String,
          userId: String,
          events: List[UserEvent],
          lastEventTime: Instant
        )

        case class ChannelState(
          activeSessions: Map[String, SessionState],
          sessionsToEmit: List[Session]
        )

        def channel(
          state: ChannelState,
          sessionCounter: Long
        ): ZChannel[Any, ZNothing, Chunk[UserEvent], Any, ZNothing, Chunk[Session], Any] =
          ZChannel.readWithCause(
            elem => {
              var newState = state
              var counter = sessionCounter

              elem.foreach { event =>
                val userId = event.userId
                val gapThresholdMs = gapThreshold.toMillis

                // Check if we have an active session for this user
                val updatedSessions = newState.activeSessions.get(userId) match {
                  case Some(existingSession) =>
                    val timeSinceLastEvent =
                      java.time.Duration
                        .between(existingSession.lastEventTime, event.timestamp)
                        .toMillis

                    if (timeSinceLastEvent > gapThresholdMs) {
                      // Gap exceeded - session ended
                      val completedSession = Session(
                        sessionId = existingSession.sessionId,
                        userId = existingSession.userId,
                        startTime = existingSession.events.head.timestamp,
                        endTime = existingSession.lastEventTime,
                        duration = java.time.Duration.between(
                          existingSession.events.head.timestamp,
                          existingSession.lastEventTime
                        ),
                        events = existingSession.events
                      )

                      newState =
                        newState.copy(sessionsToEmit =
                          newState.sessionsToEmit :+ completedSession
                        )

                      // Start a new session
                      counter += 1
                      newState.activeSessions + (userId ->
                        SessionState(
                          sessionId = s"session-${counter}",
                          userId = userId,
                          events = List(event),
                          lastEventTime = event.timestamp
                        ))
                    } else {
                      // Continue session
                      newState.activeSessions + (userId ->
                        SessionState(
                          sessionId = existingSession.sessionId,
                          userId = userId,
                          events = existingSession.events :+ event,
                          lastEventTime = event.timestamp
                        ))
                    }

                  case None =>
                    // Start new session
                    counter += 1
                    newState.activeSessions + (userId ->
                      SessionState(
                        sessionId = s"session-${counter}",
                        userId = userId,
                        events = List(event),
                        lastEventTime = event.timestamp
                      ))
                }

                newState = newState.copy(activeSessions = updatedSessions)
              }

              // Emit any completed sessions first
              if (newState.sessionsToEmit.nonEmpty) {
                val toEmit = newState.sessionsToEmit
                ZChannel.write(Chunk.fromIterable(toEmit)) *>
                  channel(newState.copy(sessionsToEmit = List()), counter)
              } else {
                channel(newState, counter)
              }
            },
            err => {
              // On error, emit any buffered completed sessions and remaining active sessions
              val completedSessions = state.sessionsToEmit

              val remainingSessions = state.activeSessions.values.map { s =>
                Session(
                  sessionId = s.sessionId,
                  userId = s.userId,
                  startTime = s.events.head.timestamp,
                  endTime = s.lastEventTime,
                  duration = java.time.Duration.between(
                    s.events.head.timestamp,
                    s.lastEventTime
                  ),
                  events = s.events
                )
              }.toList

              val allSessions = completedSessions ++ remainingSessions

              if (allSessions.nonEmpty) {
                ZChannel.write(Chunk.fromIterable(allSessions)) *>
                  ZChannel.failCause(err)
              } else {
                ZChannel.failCause(err)
              }
            },
            done => {
              // Emit any remaining active sessions as completed
              val remainingSessions = state.activeSessions.values.map { s =>
                Session(
                  sessionId = s.sessionId,
                  userId = s.userId,
                  startTime = s.events.head.timestamp,
                  endTime = s.lastEventTime,
                  duration = java.time.Duration.between(
                    s.events.head.timestamp,
                    s.lastEventTime
                  ),
                  events = s.events
                )
              }.toList

              if (remainingSessions.nonEmpty) {
                ZChannel.write(Chunk.fromIterable(remainingSessions)) *>
                  ZChannel.succeed(done)
              } else {
                ZChannel.succeed(done)
              }
            }
          )

        ZPipeline.fromChannel(
          channel(ChannelState(Map.empty, List()), 0)
        )
      }

    }

    // --- Example Showcase ---

    object Exercise3Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 3: Sessionization Pipeline ===")

        // Example 1: Simple sessionization with inactivity gap
        _ <- Console.printLine(
               "\n--- Example 1: User Sessions with 3-second Inactivity Gap ---"
             )
        _ <- Console.printLine(
               "Creating sessions from user events grouped by inactivity"
             )
        _ <- Console.printLine(
               "Timeline: user-1@t=0ms, user-1@t=500ms, user-1@t=1000ms, gap=4s, user-1@t=5000ms, etc.\n"
             )

        _ <- {
          val baseTime = Instant.now()
          val events = ZStream(
            UserEvent("user-1", baseTime),
            UserEvent("user-1", baseTime.plusMillis(500)),
            UserEvent("user-1", baseTime.plusMillis(1000)),
            UserEvent("user-1", baseTime.plusSeconds(5)), // New session (gap > 3s)
            UserEvent("user-2", baseTime.plusMillis(200)),
            UserEvent("user-2", baseTime.plusMillis(2000)),
            UserEvent("user-1", baseTime.plusSeconds(6))
          )

          events
            .via(Solution.sessionize(3.seconds))
            .foreach { session =>
              Console.printLine(
                s"  Session: ${session.sessionId} | User: ${session.userId} | Events: ${session.events.length} | Duration: ${session.duration.toMillis}ms"
              )
            }
        }

        // Example 2: Multiple concurrent users
        _ <- Console.printLine(
               "\n--- Example 2: Multiple Concurrent Users ---"
             )
        _ <- Console.printLine(
               "Gap threshold: 2 seconds. Track alice and bob independently.\n"
             )

        _ <- {
          val baseTime = Instant.now()
          val multiUserEvents = ZStream(
            UserEvent("alice", baseTime),
            UserEvent("bob", baseTime.plusMillis(100)),
            UserEvent("alice", baseTime.plusMillis(500)),
            UserEvent("bob", baseTime.plusMillis(600)),
            UserEvent("alice", baseTime.plusMillis(1000)),
            UserEvent("alice", baseTime.plusSeconds(5)), // Alice new session (gap > 2s)
            UserEvent("bob", baseTime.plusSeconds(4))    // Bob continues (gap < 2s)
          )

          multiUserEvents
            .via(Solution.sessionize(2.seconds))
            .foreach { session =>
              Console.printLine(
                s"  ${session.sessionId} (${session.userId}): ${session.events.length} events, duration: ${session.duration.toMillis}ms"
              )
            }
        }
      } yield ()
    }

  }

}
