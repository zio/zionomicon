package zionomicon.exercises

package StreamsAdvancedOperations {

  /**
   *   1. Create an infinite stream of the Fibonacci sequence using
   *      `ZStream.unfold`.
   */
  package FibonacciStream {}

  /**
   *   2. Create a stream transformation that computes the running average of
   *      all integer elements seen so far using `ZStream.mapAccum`.
   */
  package RunningAverage {}

  /**
   *   3. Create a stream transformation that computes the moving average of the
   *      last N elements using `ZStream.scan`.
   */
  package MovingAverage {}

  /**
   *   4. Implement a stream transformation that deduplicates elements within a
   *      sliding time window while preserving order.
   */
  package SlidingTimeWindowDeduplication {}

  /**
   *   5. Create a stream that paginates through GitHub's REST API to fetch all
   *      repositories from the ZIO organization.
   *
   * Hint: Use the `ZStream.paginateZIO` operator to fetch all pages by passing
   * the "page" path parameter to the
   * `https://api.github.com/orgs/zio/repos?page=<page_number>` endpoint.
   */
  package GitHubRepositoriesPagination {}

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
  package UserEventCounting {}

  /**
   *   7. Create a simple program that broadcasts a stream of integers to three
   *      consumers:
   *
   *   - One consumer that prints only even numbers
   *   - One consumer that prints only odd numbers
   *   - One consumer that prints all numbers multiplied by 10
   */
  package StreamBroadcasting {}

}
