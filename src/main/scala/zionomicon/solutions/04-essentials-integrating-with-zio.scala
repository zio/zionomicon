package zionomicon.solutions

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
 */
package Exercise1 {
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
  }
}

/**
 * Write a ZIO program that uses RabbitMQ AMQP client to connect to RabbitMQ
 * server and publish arbitrary messages to a queue. This demonstrates
 * integrating with Java/AMQP libraries through ZIO.
 *
 * The RabbitMQ AMQP client is a standard Java client for RabbitMQ.
 * See: https://www.rabbitmq.com/api-guide.html
 */
package Exercise2 {
  import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}

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
    val live: ZLayer[Channel, Throwable, MessagePublisher] =
      ZLayer.fromFunction { (channel: Channel) =>
        new MessagePublisher {
          def publishToQueue(queueName: String, message: String): Task[Unit] =
            ZIO.attempt {
              channel.queueDeclare(queueName, false, false, false, null)
              channel.basicPublish("", queueName, null, message.getBytes())
            }

          def publishToExchange(
            exchangeName: String,
            routingKey: String,
            message: String
          ): Task[Unit] =
            ZIO.attempt {
              channel.exchangeDeclare(exchangeName, "direct", false)
              channel.basicPublish(
                exchangeName,
                routingKey,
                null,
                message.getBytes()
              )
            }
        }
      }
  }

  // RabbitMQ Connection and Channel Layers
  object RabbitMQ {
    val connectionLive: ZLayer[Any, Throwable, Connection] =
      ZLayer.scoped {
        ZIO.attempt {
          val factory = new ConnectionFactory()
          factory.setHost("localhost")
          factory.setPort(5672)
          factory.setUsername("guest")
          factory.setPassword("guest")
          factory.newConnection()
        }.flatMap { connection =>
          ZIO.addFinalizer(ZIO.attempt(connection.close()).ignoreLogged).as(connection)
        }
      }

    val channelLive: ZLayer[Connection, Throwable, Channel] =
      ZLayer.scoped {
        ZIO.service[Connection].flatMap { connection =>
          ZIO.attempt(connection.createChannel()).flatMap { channel =>
            ZIO.addFinalizer(ZIO.attempt(channel.close()).ignoreLogged).as(channel)
          }
        }
      }
  }

  object Main extends ZIOAppDefault {
    val program: ZIO[MessagePublisher, Throwable, Unit] = for {
      publisher <- ZIO.service[MessagePublisher]

      // Publish to a queue
      _ <- publisher.publishToQueue(
             "user-queue",
             """{"id": 1, "name": "Alice", "email": "alice@example.com"}"""
           )
      _ <- Console.printLine("✓ Published message to user-queue")

      // Publish to an exchange with routing key
      _ <- publisher.publishToExchange(
             "user-events",
             "user.created",
             """{"userId": 1, "event": "user_created", "timestamp": "2026-04-01T00:00:00Z"}"""
           )
      _ <- Console.printLine("✓ Published message to user-events exchange with routing key user.created")

      // Publish multiple messages
      messages = List(
        ("orders-queue", """{"orderId": 101, "status": "pending"}"""),
        ("orders-queue", """{"orderId": 102, "status": "completed"}"""),
        ("orders-queue", """{"orderId": 103, "status": "shipped"}""")
      )
      _ <- ZIO.foreachDiscard(messages) { case (queue, msg) =>
             publisher.publishToQueue(queue, msg) *>
               Console.printLine(s"✓ Published to $queue")
           }

      // Publish to different exchanges
      _ <- publisher.publishToExchange(
             "orders-events",
             "order.created",
             """{"orderId": 101, "event": "order_created"}"""
           )
      _ <- Console.printLine("✓ Published to orders-events exchange")

    } yield ()

    def run =
      program.provide(
        RabbitMQ.connectionLive,
        RabbitMQ.channelLive,
        MessagePublisher.live
      )
  }
}
