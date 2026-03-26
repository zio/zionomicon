package zionomicon.solutions

import zio._
import zio.stream._
import java.time.Instant
import scala.math.Ordering

package StreamingPipelines {

  /**
   *   1. Create a pipeline that groups consecutive elements into pairs:
   *
   * {{{
   * def pair[A]: ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   * }}}
   */
  package PairPipeline {

    object Solution {

      def pair[A]: ZPipeline[Any, Nothing, A, (A, A)] = {

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

    }

    // --- Example Showcase ---

    object Exercise1Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 1: Pair Pipeline ===")

        // Example 1: Simple numeric pairing
        _ <- Console.printLine("\n--- Example 1: Numeric Pairing ---")
        _ <- Console.printLine(
               "Creating pairs from consecutive integers: 1, 2, 3, 4, 5"
             )

        _ <- ZStream(1, 2, 3, 4, 5)
               .via(Solution.pair[Int])
               .foreach(pair => Console.printLine(s"  Pair: ${pair._1} -> ${pair._2}"))

        // Example 2: String pairing
        _ <- Console.printLine("\n--- Example 2: String Pairing ---")
        _ <- Console.printLine(
               "Creating character pairs from a sequence"
             )

        _ <- ZStream("a", "b", "c", "d", "e")
               .via(Solution.pair[String])
               .foreach(pair =>
                 Console.printLine(s"  Pair: (${pair._1}, ${pair._2})")
               )

        // Example 3: Empty and single element streams
        _ <- Console.printLine("\n--- Example 3: Edge Cases ---")
        _ <- Console.printLine("Empty stream:")

        emptyCount <- ZStream.empty
                        .via(Solution.pair[Int])
                        .runCount
        _ <- Console.printLine(s"  Result: $emptyCount pairs (expected 0)")

        _ <- Console.printLine("Single element stream:")

        singleCount <- ZStream(42)
                         .via(Solution.pair[Int])
                         .runCount
        _ <- Console.printLine(s"  Result: $singleCount pairs (expected 0)")
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
