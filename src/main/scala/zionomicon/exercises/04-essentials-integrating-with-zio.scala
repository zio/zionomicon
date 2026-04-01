package zionomicon.exercises

import zio._

/**
 * Create a ZIO program that uses Doobie to perform a database operation.
 * Implement a function that inserts a user into a database and returns
 * the number of affected rows. Use the following table structure:
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
 * - Use doobie.ConnectionIO to describe database operations
 * - Create a HikariTransactor using zio-interop-cats
 * - Implement a UserService layer that uses the transactor
 * - Use ZIO.scoped to manage the transactor's lifecycle
 */
package Exercise1 {
  object Main extends ZIOAppDefault {
    def run = ???
  }
}

/**
 * Write a ZIO program that uses lepus to connect to RabbitMQ server and
 * publish arbitrary messages to a queue. Lepus is a purely functional
 * Scala client for RabbitMQ. You can find the library homepage
 * [here](http://lepus.hnaderi.dev/).
 *
 * Tips:
 * - Implement an AmqpConnection to represent the RabbitMQ connection
 * - Create a MessagePublisher trait with publishing methods
 * - Build a ZLayer for AmqpConnection and MessagePublisher
 * - Handle message publishing to both queues and exchanges with routing keys
 * - Use dependency injection via ZLayer to provide the connection to services
 * - Support both direct queue publishing and exchange-based routing
 */
package Exercise2 {
  // TODO: Define AmqpConnection case class
  // TODO: Define MessagePublisher trait with publishToQueue and publishToExchange methods
  // TODO: Implement MessagePublisher.live ZLayer
  // TODO: Create RabbitMQ.connectionLive ZLayer
  // TODO: Implement Main program that publishes messages to queues and exchanges

  object Main extends ZIOAppDefault {
    def run = ???
  }
}
