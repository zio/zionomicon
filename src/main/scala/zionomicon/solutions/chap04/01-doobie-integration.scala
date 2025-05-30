/**
 *   1. Create a ZIO program that uses Doobie to perform a database operation.
 *      Implement a function that inserts a user into a database and returns
 *      the number of affected rows. Use the following table structure:
 *
 *      ```sql
 *      CREATE TABLE users (
 *        id SERIAL PRIMARY KEY,
 *        name TEXT NOT NULL,
 *        age INT NOT NULL
 *      )
 *      ```
 */

//> using scala "2.13.16"
//> using dep "dev.zio::zio:2.1.18"
//> using dep "dev.zio::zio-interop-cats:23.1.0.5"
//> using dep "org.tpolecat::doobie-core:1.0.0-RC9"
//> using dep "org.tpolecat::doobie-hikari:1.0.0-RC9"
//> using dep "org.xerial:sqlite-jdbc:3.49.1.0"

package zionomicon.solutions.chap04

import com.zaxxer.hikari.HikariConfig
import doobie._
import doobie.hikari._
import doobie.implicits._
import zio._
import zio.interop.catz._

// User case class
case class User(name: String, age: Int)

// Database operations
object UserRepository {

  // Insert a user and return the number of affected rows
  def insertUser(user: User): ConnectionIO[Int] =
    sql"""
      INSERT INTO users (name, age)
      VALUES (${user.name}, ${user.age})
    """.update.run

  // Insert a user and return the generated ID
  def insertUserWithId(user: User): ConnectionIO[Long] =
    sql"""
      INSERT INTO users (name, age)
      VALUES (${user.name}, ${user.age})
    """.update.withUniqueGeneratedKeys[Long]("id")

  // Get all users (for verification)
  def getAllUsers: ConnectionIO[List[(Long, String, Int)]] =
    sql"""
      SELECT id, name, age FROM users
    """.query[(Long, String, Int)].to[List]

  // Create table if not exists
  def createTable: ConnectionIO[Int] =
    sql"""
      CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        age INTEGER NOT NULL
      )
    """.update.run
}

// ZIO Layer for Database Transactor
object Database {

  private def hikariConfig: HikariConfig = {
    val config = new HikariConfig()
    config.setDriverClassName("org.sqlite.JDBC")
    config.setJdbcUrl("jdbc:sqlite:users.db")
    config.setMaximumPoolSize(1) // SQLite doesn't support concurrent writes
    config
  }

  val transactorLive: ZLayer[Any, Throwable, HikariTransactor[Task]] =
    ZLayer.scoped {
      for {
        ec <- ZIO.executor.map(_.asExecutionContext)
        xa <- HikariTransactor
                .fromHikariConfigCustomEc[Task](hikariConfig, ec)
                .toScopedZIO
      } yield xa
    }
}

// Service layer
trait UserService {
  def insertUser(user: User): Task[Int]
  def insertUserWithId(user: User): Task[Long]
  def getAllUsers: Task[List[(Long, String, Int)]]
  def initializeDatabase: Task[Unit]
}

object UserService {

  val live: ZLayer[HikariTransactor[Task], Nothing, UserService] =
    ZLayer.fromFunction { (xa: HikariTransactor[Task]) =>
      new UserService {
        def insertUser(user: User): Task[Int] =
          UserRepository.insertUser(user).transact(xa)

        def insertUserWithId(user: User): Task[Long] =
          UserRepository.insertUserWithId(user).transact(xa)

        def getAllUsers: Task[List[(Long, String, Int)]] =
          UserRepository.getAllUsers.transact(xa)

        def initializeDatabase: Task[Unit] =
          UserRepository.createTable.transact(xa).unit
      }
    }
}

object Main extends ZIOAppDefault {

  val program: ZIO[UserService, Throwable, Unit] = for {
    service <- ZIO.service[UserService]

    // Initialize database
    _ <- service.initializeDatabase
    _ <- Console.printLine("Database initialized")

    // Insert users
    user1 = User("Alice", 25)
    user2 = User("Bob", 30)
    user3 = User("Charlie", 35)

    // Insert user and get affected rows
    affectedRows1 <- service.insertUser(user1)
    _ <- Console.printLine(
           s"Inserted ${user1.name}, affected rows: $affectedRows1"
         )

    // Insert users and get generated IDs
    id2 <- service.insertUserWithId(user2)
    _   <- Console.printLine(s"Inserted ${user2.name} with ID: $id2")

    id3 <- service.insertUserWithId(user3)
    _   <- Console.printLine(s"Inserted ${user3.name} with ID: $id3")

    // Verify by getting all users
    _     <- Console.printLine("\nAll users in database:")
    users <- service.getAllUsers
    _ <- ZIO.foreach(users) { case (id, name, age) =>
           Console.printLine(s"  ID: $id, Name: $name, Age: $age")
         }

  } yield ()

  def run =
    program
      .provide(
        Database.transactorLive,
        UserService.live
      )
      .tapError(error => Console.printLineError(s"Error: ${error.getMessage}"))
      .exitCode
}

// To run this script:
// 1. Run: scala-cli run zio-doobie-user.scala
// 2. The SQLite database file 'users.db' will be created automatically
