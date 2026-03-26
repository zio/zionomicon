package zionomicon.exercises

package StreamingPipelines {

  /**
   *   1. Create a pipeline that groups consecutive elements into pairs.
   *      Choose one or both options:
   *
   *      Option A: Overlapping pairs
   *        Input:  1, 2, 3, 4, 5
   *        Output: (1,2), (2,3), (3,4), (4,5)
   *        Use case: Detecting changes/trends between consecutive elements
   *
   *      Option B: Non-overlapping pairs
   *        Input:  1, 2, 3, 4, 5
   *        Output: (1,2), (3,4)  [5 is unpaired and discarded]
   *        Use case: Batch processing, fixed-size chunking
   *
   * {{{
   * def pairOverlapping[A]: ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   *
   * def pairNonOverlapping[A]: ZPipeline[Any, Nothing, A, (A, A)] =
   *   ???
   *
   * // Or implement both!
   * }}}
   */
  package PairPipeline {}

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
  package MinMaxWindow {}

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
  package Sessionize {}

}
