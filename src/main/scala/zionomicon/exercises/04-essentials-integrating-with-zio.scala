package zionomicon.exercises

import zio._

object EssentialsIntegratingWithZIO {

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
  object Exercise1 {}

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
