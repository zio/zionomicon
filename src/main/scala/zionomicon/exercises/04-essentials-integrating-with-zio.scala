package zionomicon.exercises

import zio._

object EssentialsIntegratingWithZIO {

  /**
   * Create a ZIO program that uses Doobie to perform a database operation.
   * Implement a function that inserts a user into a database and returns the
   * number of affected rows. Use SQLite with the following table structure:
   *
   * ```sql
   * CREATE TABLE users (
   *   id INTEGER PRIMARY KEY AUTOINCREMENT,
   *   name TEXT NOT NULL,
   *   age INTEGER NOT NULL
   * )
   * ```
   *
   * Tips:
   *   - Use doobie.ConnectionIO to describe database operations
   *   - Create a HikariTransactor using zio-interop-cats
   *   - Implement a UserService layer that uses the transactor
   *   - Use ZIO.scoped to manage the transactor's lifecycle
   */
  object Exercise1 {
    def run = ???
  }

  /**
   * Write a ZIO program that uses Lepus to connect to RabbitMQ server and
   * publish arbitrary messages to a queue. Lepus is a purely functional Scala
   * client for RabbitMQ. You can find the library homepage
   * [here](http://lepus.hnaderi.dev/).
   *
   * To run this example, ensure RabbitMQ is running locally (or adjust the
   * configuration in the code). You can start a RabbitMQ instance with Docker:
   *
   * {{{
   *   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
   * }}}
   *
   * Tips:
   *   - Use lepus.client.{LepusClient, Connection, Channel}
   *   - Create a connection using LepusClient[Task]()
   *   - Use connection.channel to get a Channel
   *   - Use channel.queue.declare to declare a queue
   *   - Use channel.messaging.publish to publish messages
   *   - Handle ExchangeType for routing messages with exchanges
   *   - Use ZIO's scoped resources to manage connection lifecycle
   */
  object Exercise2 {
    // TODO: Import Lepus client types and utilities
    // TODO: Create a connection to RabbitMQ using LepusClient[Task]()
    // TODO: Declare a queue and publish messages to it
    // TODO: Implement publishing to exchanges with routing keys
    // TODO: Demonstrate both queue and topic exchange patterns
    def run = ???
  }

}
