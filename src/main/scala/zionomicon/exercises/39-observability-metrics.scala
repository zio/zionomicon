package zionomicon.exercises

package ObservabilityMetrics {

  /**
   *   1. In this chapter, we wrote two metric clients for Prometheus and
   *      StatsD. To make your client-related code more reusable, you can
   *      extract it and put it in a separate ZIO application. Then you can
   *      compose that application with your main application to run them
   *      together.
   *
   * {{{
   * import zio._
   * import zio.metrics._
   * import zio.metrics.connectors._
   * import zio.metrics.connectors.prometheus._
   * import zio.http._
   * import java.nio.charset.StandardCharsets
   *
   * // Step 1: Create a metrics server application
   * val metricsServerApp: ZIOApp = ???
   *
   * // Step 2: Create a main application with some business logic
   * val businessLogicApp: ZIOApp = ???
   *
   * // Step 3: Compose both applications together using the <> operator
   * val composedApp: ZIOApp = ???
   *
   * // Hint: Use the ZIOAppDefault#<> operator to compose two ZIO applications.
   * // The metricsServerApp should expose metrics on an HTTP endpoint,
   * // while businessLogicApp should track metrics and run the main logic.
   * }}}
   */
  package MetricClientComposition {}

}
