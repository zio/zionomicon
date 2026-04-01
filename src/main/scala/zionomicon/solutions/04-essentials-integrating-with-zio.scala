package zionomicon.solutions

import zio._

object EssentialsIntegratingWithZIO {

  /**
   * Write a ZIO program that uses lepus to connect to RabbitMQ server and
   * publish arbitrary messages to a queue. Lepus is a purely functional
   * Scala client for RabbitMQ. You can find the library homepage
   * [here](http://lepus.hnaderi.dev/).
   */
  object Exercise2 {
    import dev.lepus.client.api._
    import zio.interop.catz._

    // Message Publisher Service
    trait MessagePublisher {
      def publishToQueue(queueName: String, message: String): Task[Unit]
      def publishToExchange(
        exchangeName: String,
        routingKey: String,
        message: String
      ): Task[Unit]
    }

    object MessagePublisher {
      val live: ZLayer[AmqpConnection, Throwable, MessagePublisher] = ???
    }

    object Main extends ZIOAppDefault {
      def run = ???
    }
  }
}
