package zionomicon.solutions

package ObservabilityMetrics {

  /**
   *   1. In this chapter, we wrote two metric clients for Prometheus and
   *      StatsD. To make your client-related code more reusable, you can
   *      extract it and put it in a separate ZIO application. Then you can
   *      compose that application with your main application to run them
   *      together.
   */
  package MetricClientComposition {
    import zio._
    import zio.metrics._

    // Step 1: Create a metrics client application
    // This application periodically captures and logs metrics snapshots
    object MetricsClientApp extends ZIOAppDefault {
      private val metricsCollection: ZIO[Any, Nothing, Unit] =
        for {
          snapshot <- ZIO.metrics
          _        <- snapshot.prettyPrint.debug("Metrics snapshot")
        } yield ()

      def run = metricsCollection.repeat(Schedule.fixed(5.seconds))
    }

    // Step 2: Create a business logic application that tracks metrics
    object BusinessLogicApp extends ZIOAppDefault {
      private val effect = ZIO.debug("Processing request...")

      // Counter metric with tags for better categorization
      private val taggedCounter =
        Metric
          .counter("total_requests", "Total number of requests processed")
          .fromConst(1L)
          .tagged("service", "business-logic")
          .tagged("environment", "production")

      def run =
        (effect @@ taggedCounter)
          .repeat(Schedule.exponential(100.milliseconds, 2.0))
    }

    // Step 3: Compose both applications together using the <> operator
    // The <> operator combines the layers and runs both applications in parallel
    object ComposedMetricsApp
        extends ZIOApp.Proxy(
          MetricsClientApp <> BusinessLogicApp
        )

  }

}
