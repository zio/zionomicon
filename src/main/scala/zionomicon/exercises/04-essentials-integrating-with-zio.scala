package zionomicon.exercises

import zio._

/**
 * Create a ZIO program that uses Doobie to perform a database operation.
 * Implement a function that inserts a user into a database and returns the
 * number of affected rows. Use the following table structure:
 *
 * ```sql
 * CREATE TABLE users (
 *   id SERIAL PRIMARY KEY,
 *   name TEXT NOT NULL,
 *   age INT NOT NULL
 * )
 * ```
 *
 * Tips:
 *   - Use doobie.ConnectionIO to describe database operations
 *   - Create a HikariTransactor using zio-interop-cats
 *   - Implement a UserService layer that uses the transactor
 *   - Use ZIO.scoped to manage the transactor's lifecycle
 */
package Exercise1 {
  object Main extends ZIOAppDefault {
    def run = ???
  }
}

/**
 * Write a ZIO program that uses RabbitMQ AMQP client to connect to RabbitMQ
 * server and publish arbitrary messages to a queue. This demonstrates
 * integrating with Java/AMQP libraries through ZIO.
 *
 * The RabbitMQ AMQP client is a standard Java client for RabbitMQ. See:
 * https://www.rabbitmq.com/api-guide.html
 *
 * Tips:
 *   - Use com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
 *   - Create a MessagePublisher trait with publishToQueue and publishToExchange
 *     methods
 *   - Implement ZLayer for Connection using ZLayer.scoped with proper resource
 *     cleanup
 *   - Implement ZLayer for Channel that depends on Connection
 *   - Use ZIO.attempt to wrap Java side-effects
 *   - Use ZIO.addFinalizer for proper resource cleanup (close connections and
 *     channels)
 *   - Use ZIO.service to access dependencies from the environment
 *   - Compose layers using ZLayer.provide in Main.run
 */
package Exercise2 {
  // TODO: Import ConnectionFactory, Connection, Channel from com.rabbitmq.client
  // TODO: Define MessagePublisher trait with publishToQueue and publishToExchange methods
  // TODO: Implement MessagePublisher.live ZLayer using Channel dependency
  // TODO: Create RabbitMQ.connectionLive ZLayer with proper finalization
  // TODO: Create RabbitMQ.channelLive ZLayer that depends on Connection
  // TODO: Implement Main program that publishes messages to queues and exchanges
  // TODO: Compose all layers in Main.run using .provide(...)

  object Main extends ZIOAppDefault {
    def run = ???
  }
}
