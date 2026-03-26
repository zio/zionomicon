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
   *      a continuous data stream within a fixed time window (e.g., every minute).
   *
   * {{{
   * def minMaxWindow[A: Ordering](
   *   windowSize: Duration
   * ): ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   * }}}
   */
  package MinMaxWindow {

    object Solution {

      def minMaxWindow[A: Ordering](
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

        ZPipeline.fromChannel(channel(Vector.empty, java.lang.System.currentTimeMillis()))
      }

    }

    // --- Example Showcase ---

    object Exercise2Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 2: Min-Max Window Pipeline ===")

        // Example 1: Numeric data with time windows
        _ <- Console.printLine(
               "\n--- Example 1: Numeric Min-Max in 500ms Windows ---"
             )
        _ <- Console.printLine(
               "Stream of integers arriving over time, computing min/max every 500ms"
             )

        _ <- ZStream(5, 2, 8, 1, 9, 3, 7)
               .schedule(Schedule.spaced(100.millis))
               .via(Solution.minMaxWindow[Int](500.millis))
               .foreach(minMax =>
                 Console.printLine(s"  Window result: min=${minMax._1}, max=${minMax._2}")
               )

        // Example 2: Double values
        _ <- Console.printLine("\n--- Example 2: Double Values Min-Max ---")

        _ <- ZStream(1.5, 2.3, 0.8, 4.2, 1.1)
               .schedule(Schedule.spaced(80.millis))
               .via(Solution.minMaxWindow[Double](400.millis))
               .foreach(minMax =>
                 Console.printLine(
                   s"  Window result: min=${minMax._1}, max=${minMax._2}"
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
            err => ZChannel.failCause(err),
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

        now = Instant.now()

        events = ZStream(
          UserEvent("user-1", now),
          UserEvent("user-1", now.plusMillis(500)),
          UserEvent("user-1", now.plusMillis(1000)),
          UserEvent("user-1", now.plusSeconds(5)), // New session (gap > 3s)
          UserEvent("user-2", now.plusMillis(200)),
          UserEvent("user-2", now.plusMillis(2000)),
          UserEvent("user-1", now.plusSeconds(6))
        )

        _ <- events
               .via(Solution.sessionize(3.seconds))
               .foreach { session =>
                 Console.printLine(
                   s"  Session: ${session.sessionId} | User: ${session.userId} | Events: ${session.events.length}"
                 )
               }

        // Example 2: Multiple concurrent users
        _ <- Console.printLine(
               "\n--- Example 2: Multiple Concurrent Users ---"
             )

        multiUserEvents = ZStream(
          UserEvent("alice", now),
          UserEvent("bob", now.plusMillis(100)),
          UserEvent("alice", now.plusMillis(500)),
          UserEvent("bob", now.plusMillis(600)),
          UserEvent("alice", now.plusMillis(1000)),
          UserEvent("alice", now.plusSeconds(5)), // Alice new session
          UserEvent("bob", now.plusSeconds(4))
        )

        _ <- multiUserEvents
               .via(Solution.sessionize(2.seconds))
               .foreach { session =>
                 Console.printLine(
                   s"  ${session.sessionId} (${session.userId}): ${session.events.length} events, duration: ${session.duration.toMillis}ms"
                 )
               }
      } yield ()
    }

  }

}
