package zionomicon.solutions

import zio._

object EssentialsIntegratingWithZIO_Solutions {

  /**
   * Create a ZIO program that uses Doobie to perform a database operation.
   * Implement a function that inserts a user into a database and returns the
   * number of affected rows. Use the following table structure:
   *
   * ```sql
   * CREATE TABLE users (
   *   id INTEGER PRIMARY KEY AUTOINCREMENT,
   *   name TEXT NOT NULL,
   *   age INTEGER NOT NULL
   * )
   * ```
   */
  object Exercise1 {
    import com.zaxxer.hikari.HikariConfig
    import doobie._
    import doobie.hikari._
    import doobie.implicits._
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
        _ <- ZIO.foreachDiscard(users) { case (id, name, age) =>
               Console.printLine(s"  ID: $id, Name: $name, Age: $age")
             }

      } yield ()

      def run =
        program
          .provide(
            Database.transactorLive,
            UserService.live
          )
          .tapError(error =>
            Console.printLineError(s"Error: ${error.getMessage}")
          )
    }
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
   */
  object Exercise2 {
    import cats.effect.std.Console as CatsConsole
    import lepus.client._
    import lepus.protocol.domains._
    import zio.interop.catz._
    import zio.{Console, Task, ZIO, ZIOAppDefault}

    object Main extends ZIOAppDefault {
      implicit lazy val taskConsole: CatsConsole[Task] = CatsConsole.make[Task]

      def app(conn: Connection[Task]): Task[Unit] =
        conn.channel.use { channel =>
          for {
            // Publish to the default exchange with a queue name as routing key
            qname <- ZIO
                       .fromEither(QueueName.from("test-queue"))
                       .mapError(msg => new Exception(msg))
            _ <- channel.queue.declare(qname)
            _ <- channel.messaging.publish(
                   exchange = ExchangeName(""),
                   routingKey = qname,
                   Message("Hello from ZIO and Lepus!")
                 )
            _ <- Console.printLine("✓ Published message to test-queue")

            _ <- channel.messaging.publish(
                   exchange = ExchangeName(""),
                   routingKey = qname,
                   Message(
                     """{"event": "user_created", "userId": 123, "timestamp": "2026-04-03"}"""
                   )
                 )
            _ <- Console.printLine("✓ Published JSON message to test-queue")

            // Publish to a topic exchange with different routing keys
            exname <- ZIO
                        .fromEither(ExchangeName.from("events-exchange"))
                        .mapError(msg => new Exception(msg))
            _ <- channel.exchange.declare(exname, ExchangeType.Topic)
            rk1 <- ZIO
                     .fromEither(ShortString.from("user.created"))
                     .mapError(msg => new Exception(msg))
            _ <- channel.messaging.publish(
                   exchange = exname,
                   routingKey = rk1,
                   Message("""{"event": "user_created", "userId": 123}""")
                 )
            _ <-
              Console.printLine(
                "✓ Published message to events-exchange with routing key user.created"
              )

            rk2 <- ZIO
                     .fromEither(ShortString.from("order.completed"))
                     .mapError(msg => new Exception(msg))
            _ <- channel.messaging.publish(
                   exchange = exname,
                   routingKey = rk2,
                   Message("""{"event": "order_completed", "orderId": 456}""")
                 )
            _ <-
              Console.printLine(
                "✓ Published message to events-exchange with routing key order.completed"
              )
          } yield ()
        }

      def run: ZIO[Scope, Throwable, Unit] =
        for {
          conn <- LepusClient[Task]().toScopedZIO
          _    <- app(conn)
        } yield ()
    }
  }

}
