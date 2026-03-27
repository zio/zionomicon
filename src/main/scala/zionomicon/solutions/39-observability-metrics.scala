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

    // Step 1: Create a metrics client application that periodically publishes metrics
    object MetricsClientApp extends ZIOAppDefault {
      def run =
        for {
          _ <- ZIO.logInfo("Metrics client started")
          _ <-
            ZIO.never.onInterrupt(_ => ZIO.logInfo("Metrics client stopped!"))
        } yield ()
    }

    // Step 2: Create a main application with business logic that tracks metrics
    object BusinessLogicApp extends ZIOAppDefault {
      private val effect = ZIO.debug("Processing request...")

      private val mainApp =
        (effect @@ Metric.counter("total_requests").fromConst(1L))
          .repeat(Schedule.exponential(100.milliseconds, 2.0))

      def run = mainApp
    }

    // Step 3: Compose both applications together
    // This demonstrates how to structure a ZIO application that runs both
    // the metrics client and business logic concurrently
    object ComposedMetricsApp extends ZIOAppDefault {
      private val businessLogic =
        (ZIO.debug("Processing...") @@ Metric
          .counter("total_requests")
          .fromConst(1L))
          .repeat(Schedule.exponential(100.milliseconds, 2.0))

      private val metricsCollection: ZIO[Any, Nothing, Unit] =
        for {
          _        <- ZIO.logInfo("Metrics collector started")
          snapshot <- ZIO.metrics
          _        <- snapshot.prettyPrint.debug("Current metrics")
          _        <- ZIO.sleep(5.seconds)
        } yield ()

      private val metricsCollector: ZIO[Any, Nothing, Unit] =
        metricsCollection.repeat(Schedule.fixed(5.seconds)).forkDaemon.unit

      def run =
        for {
          _ <- metricsCollector
          _ <- businessLogic
        } yield ()
    }

    // Alternative composition pattern: Using separate modules
    object ComposedAppsExample {
      // Metrics collection as a reusable effect
      def metricsCollectionEffect: ZIO[Any, Nothing, Unit] =
        for {
          snapshot <- ZIO.metrics
          _        <- snapshot.prettyPrint.debug("Metrics snapshot")
        } yield ()

      // Business logic as a separate function
      def businessLogicEffect: ZIO[Any, Nothing, Unit] =
        (ZIO.debug("Request handled") @@ Metric
          .counter("requests_processed")
          .fromConst(1L))

      // Composed application that runs both in parallel
      object MainApp extends ZIOAppDefault {
        def run =
          for {
            _ <- metricsCollectionEffect
                   .repeat(Schedule.fixed(5.seconds))
                   .forkDaemon
            _ <- businessLogicEffect.repeat(
                   Schedule.exponential(100.milliseconds, 2.0)
                 )
          } yield ()
      }
    }
  }

}
