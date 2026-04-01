package zionomicon.exercises

import zio._

object EssentialsIntegratingWithZIO {

  /**
   * Write a ZIO program that uses lepus to connect to RabbitMQ server and
   * publish arbitrary messages to a queue. Lepus is a purely functional
   * Scala client for RabbitMQ. You can find the library homepage
   * [here](http://lepus.hnaderi.dev/).
   *
   * Tips:
   * - Use AmqpClient to connect to RabbitMQ
   * - Implement a MessagePublisher service for publishing messages
   * - Handle message routing and queue declarations
   * - Use zio-interop-cats for compatibility
   */
  object Exercise2 {}
}
