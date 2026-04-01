package zionomicon.exercises.chap04.exercise1

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
object Main extends ZIOAppDefault {
  def run = ???
}
