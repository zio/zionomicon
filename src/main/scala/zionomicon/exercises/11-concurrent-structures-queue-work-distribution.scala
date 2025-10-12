package zionomicon.exercises

package QueueWorkDistribution {

  /**
   *   1. Implement load balancer that distributes work across multiple worker
   *      queues using a round-robin strategy:
   *
   * {{{
   * trait LoadBalancer[A] {
   *   def submit(work: A): Task[Unit]
   *   def shutdown: Task[Unit]
   * }
   * object LoadBalancer {
   *   def make[A](workerCount: Int, process: A => Task[A]) = ???
   * }
   * }}}
   */
  package LoadBalancerImpl {}

  /**
   *   2. Implement a rate limiter that limits the number of requests processed
   *      in a given time frame. It takes the time interval and the maximum
   *      number of calls that are allowed to be performed within the time
   *      interval:
   *
   * {{{
   * trait RateLimiter {
   *   def acquire: UIO[Unit]
   *   def apply[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A]
   * }
   *
   * object RateLimiter {
   *   def make(max: Int, interval: Duration): UIO[RateLimiter] = ???
   * }
   * }}}
   */
  package RateLimiterImpl {}

  /**
   *   3. Implement a circuit breaker that prevents calls to a service after a
   *      certain number of failures:
   *
   * {{{
   * trait CircuitBreaker {
   *   def protect[A](operation: => Task[A]): Task[A]
   * }
   * }}}
   *
   * Hint: Use a sliding queue to store the results of the most recent
   * operations and track the number of failures.
   */
  package CircuitBreakerImpl {}

}
