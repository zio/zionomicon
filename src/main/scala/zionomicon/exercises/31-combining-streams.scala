package zionomicon.exercises

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
  package CorrelateEvents {}

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
  package PriorityMerge {}

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
  package AdaptiveSampling {}

}
