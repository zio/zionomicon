package zionomicon.solutions

package ConfiguringZIOApplications {

  /**
   *   1. Build a system that watches a configuration file for changes and
   *      automatically restarts an HTTP server with the updated settings. Your
   *      solution should gracefully stop the old server before starting a new
   *      one.
   */
  package ReloadableServiceImpl {

    import zio._
    import zio.config.typesafe.TypesafeConfigProvider
    import zio.http.{Path => _, _}
    import zio.stream._

    import java.nio.file.StandardWatchEventKinds._
    import java.nio.file._
    import scala.jdk.CollectionConverters._

    /**
     * Signals for coordinating config reloads.
     */

    trait ConfigReloadSignal {
      def signal: UIO[Unit]
      def stream: UStream[Unit]
    }

    object ConfigReloadSignal {
      val live: ULayer[ConfigReloadSignal] = ZLayer.scoped {
        for {
          hub <- Hub.unbounded[Unit]
        } yield new ConfigReloadSignal {
          def signal: UIO[Unit]     = hub.publish(()).unit
          def stream: UStream[Unit] = ZStream.fromHub(hub)
        }
      }
    }

    /**
     * Watches a config file and signals when it changes.
     */
    object ConfigWatcher {

      def layer[Config: Tag](
        configPath: Path,
        loadConfig: Path => Task[Config]
      ): ZLayer[ConfigReloadSignal, Throwable, Reloadable[Config]] = {

        val configLayer: ZLayer[Any, Throwable, Config] =
          ZLayer.fromZIO(loadConfig(configPath))

        val reloadableLayer: ZLayer[Any, Throwable, Reloadable[Config]] =
          Reloadable.manual(configLayer)

        val watcherLayer: ZLayer[ConfigReloadSignal, Throwable, Unit] =
          ZLayer.scoped {
            for {
              reloadable <- reloadableLayer.build.map(_.get)
              signal     <- ZIO.service[ConfigReloadSignal]
              _ <- watchFile(
                     configPath,
                     onChanged = reloadable.reload.ignore *> signal.signal
                   )
            } yield ()
          }

        reloadableLayer >+> watcherLayer.passthrough
      }

      private def watchFile(
        configPath: Path,
        onChanged: UIO[Unit]
      ): ZIO[Scope, Throwable, Unit] = {
        val watchDir = configPath.getParent
        val fileName = configPath.getFileName

        for {
          watchService <- ZIO.acquireRelease(
                            ZIO.attempt {
                              val ws = FileSystems.getDefault.newWatchService()
                              watchDir.register(ws, ENTRY_MODIFY)
                              ws
                            }
                          )(ws => ZIO.succeed(ws.close()))

          debounceRef <- Ref.make[Option[Fiber.Runtime[Nothing, Unit]]](None)

          _ <- ZStream
                 .repeatZIO(
                   ZIO.attemptBlocking {
                     val key    = watchService.take()
                     val events = key.pollEvents().asScala.toList
                     key.reset()
                     events
                   }
                 )
                 .mapZIO { events =>
                   val hasChange = events.exists { e =>
                     e.context().asInstanceOf[Path] == fileName &&
                     e.kind() == ENTRY_MODIFY
                   }
                   ZIO.when(hasChange) {
                     for {
                       pending <- debounceRef.get
                       _       <- pending.fold(ZIO.unit)(_.interrupt.unit)
                       fiber <- (ZIO.logInfo(
                                  s"Config changed: $configPath"
                                ) *> onChanged).fork
                       _ <- debounceRef.set(Some(fiber))
                     } yield ()
                   }
                 }
                 .runDrain
                 .forkScoped
        } yield ()
      }
    }

    /**
     * Server manager that reloads the server when signaled.
     */
    trait ManagedServer {
      def port: UIO[Int]
      def server: UIO[Server]
    }

    object ManagedServer {

      def live(
        routes: Routes[Any, Response]
      ): ZLayer[Reloadable[
        Server.Config
      ] & ConfigReloadSignal, Throwable, ManagedServer] =
        ZLayer.scoped {
          for {
            reloadableConfig <- ZIO.service[Reloadable[Server.Config]]
            reloadSignal     <- ZIO.service[ConfigReloadSignal]

            // State: current server + its cleanup fiber
            serverRef <-
              Ref.make[Option[(Server, Fiber.Runtime[Nothing, Unit])]](None)

            // Start server with given config
            startServer = (config: Server.Config) =>
                            for {
                              scope <- Scope.make
                              server <-
                                Server
                                  .defaultWith(_ => config)
                                  .build
                                  .map(_.get)
                                  .provideEnvironment(ZEnvironment(scope))
                              _         <- server.install(routes)
                              port      <- server.port
                              _         <- ZIO.logInfo(s"Server started on port $port")
                              keepAlive <- scope.use(ZIO.never).fork
                            } yield (server, keepAlive)

            // Stop current server
            stopServer = serverRef.get.flatMap {
                           case Some((_, fiber)) =>
                             ZIO.logInfo("Stopping server...") *>
                               fiber.interrupt *>
                               ZIO.logInfo("Server stopped")
                           case None => ZIO.unit
                         }

            // Reload:
            //   1. stop old
            //   2. get new config
            //   3. start server with new config
            //   4. update state
            reload = for {
                       _      <- stopServer
                       config <- reloadableConfig.get
                       result <- startServer(config)
                       _      <- serverRef.set(Some(result))
                     } yield ()

            // Initial start:
            //   1. get config
            //   2. start server with config
            //   3. update state
            initialConfig <- reloadableConfig.get
            initialResult <- startServer(initialConfig)
            _             <- serverRef.set(Some(initialResult))

            // Listen for reload signals
            _ <- reloadSignal.stream
                   .tap(_ => ZIO.logInfo("Received reload signal"))
                   .mapZIO(_ =>
                     reload.catchAllCause(c =>
                       ZIO.logErrorCause("Failed to reload server", c)
                     )
                   )
                   .runDrain
                   .forkScoped

            // Cleanup on shutdown
            _ <- ZIO.addFinalizer(stopServer)

            service = new ManagedServer {
                        def port: UIO[Int] = serverRef.get.flatMap {
                          case Some((s, _)) => s.port
                          case None         => ZIO.succeed(0)
                        }
                        def server: UIO[Server] = serverRef.get.map(_.get._1)
                      }
          } yield service
        }
    }

    object ServerConfig {
      def loadFromFile(path: Path): Task[Server.Config] =
        ZIO
          .withConfigProvider(
            TypesafeConfigProvider.fromHoconFile(path.toFile)
          )(
            ZIO.config[Server.Config](Server.Config.config)
          )
          .mapError(e => new RuntimeException(s"Config load failed: $e"))
    }

    object ReloadableConfigExample extends ZIOAppDefault {

      /**
       * A sample config file (`application.conf`):
       * {{{
       * binding-host = "localhost"
       * binding-port = 8080
       * graceful-shutdown-timeout = 2000ms
       * }}}
       */
      val configPath: Path = Paths.get("./application.conf")

      val routes: Routes[Any, Response] = Routes(
        Method.GET / Root     -> handler(Response.text("OK")),
        Method.GET / "health" -> handler(Response.text("healthy"))
      )

      def run: ZIO[Any, Throwable, Unit] = {
        val program = for {
          _       <- ZIO.logInfo("=== Reloadable Config Server ===")
          _       <- ZIO.logInfo(s"Watching: $configPath")
          service <- ZIO.service[ManagedServer]
          port    <- service.port
          _       <- ZIO.logInfo(s"Ready on port $port")
          _       <- ZIO.never
        } yield ()

        program.provide(
          ConfigReloadSignal.live,
          ConfigWatcher.layer(configPath, ServerConfig.loadFromFile),
          ManagedServer.live(routes)
        )
      }
    }

  }

  /**
   *   2. As your application becomes more complex, you may need to manage
   *      different configurations for different environments, with each in a
   *      separate file, such as `Development.conf`, `Testing.conf`, and
   *      `Production.conf`. Write a config provider that loads configurations
   *      based on the environment variable `APP_ENV`.
   */
  package EnvironmentBasedConfigImpl {

    import zio._
    import zio.config.typesafe._

    /**
     * A ConfigProvider that loads configurations based on the APP_ENV
     * environment variable.
     *
     * Falls back to "Development" if APP_ENV is not set.
     */
    object EnvironmentBasedConfigProvider {

      def fromString(s: String): Option[String] = s.toLowerCase match {
        case "development" | "dev" => Some("Development.conf")
        case "testing" | "test"    => Some("Testing.conf")
        case "production" | "prod" => Some("Production.conf")
        case _                     => None
      }

      /**
       * Creates a ConfigProvider that loads from the appropriate config file
       * based on the APP_ENV environment variable.
       */
      val live: ZIO[Any, Config.Error, ConfigProvider] =
        for {
          envOpt    <- System.env("APP_ENV").orDie
          configFile = envOpt.flatMap(fromString).getOrElse("Development.conf")
          provider <-
            ZIO
              .attempt(TypesafeConfigProvider.fromHoconFilePath(configFile))
              .mapError(e =>
                Config.Error.SourceUnavailable(
                  path = Chunk.empty,
                  message = s"Could not load config file: $configFile",
                  cause = Cause.fail(e)
                )
              )
        } yield provider
    }

    // Example usage
    object ExampleApp extends ZIOAppDefault {
      case class AppConfig(host: String, port: Int, debug: Boolean)

      object AppConfig {
        implicit val config: Config[AppConfig] = {
          Config.string("host") zip
            Config.int("port") zip
            Config.boolean("debug").withDefault(false)
        }.map { case (host, port, debug) =>
          AppConfig(host, port, debug)
        }
      }

      def run =
        for {
          provider <- EnvironmentBasedConfigProvider.live
          config <- ZIO.withConfigProvider(provider) {
                      ZIO.config[AppConfig]
                    }
          _ <- Console.printLine(s"Loaded config: $config")
        } yield ()
    }
  }

  /**
   *   3. Write a configuration descriptor for the `DatabaseConfig` which only
   *      accepts either `MysqlConfig` or `SqliteConfig` configurations.
   */
  package DatabaseConfigImpl {

    import zio.Config.Secret
    import zio._

    /**
     * A sum type representing database configurations. Only MySQL or SQLite
     * configurations are accepted.
     */
    sealed trait DatabaseConfig

    object DatabaseConfig {

      case class MysqlConfig(
        host: String,
        port: Int,
        database: String,
        username: String,
        password: Secret
      ) extends DatabaseConfig

      case class SqliteConfig(
        path: String,
        inMemory: Boolean = false
      ) extends DatabaseConfig

      // Config descriptor for MySQL
      val mysqlConfig: Config[MysqlConfig] = {
        Config.string("host") zip
          Config.int("port").withDefault(3306) zip
          Config.string("database") zip
          Config.string("username") zip
          Config.secret("password")
      }.map { case (host, port, database, username, password) =>
        MysqlConfig(host, port, database, username, password)
      }.nested("mysql")

      // Config descriptor for SQLite
      val sqliteConfig: Config[SqliteConfig] = {
        Config.string("path") zip
          Config.boolean("in-memory").withDefault(false)
      }.map { case (path, inMemory) =>
        SqliteConfig(path, inMemory)
      }.nested("sqlite")

      /**
       * Combined config descriptor that accepts either MySQL or SQLite.
       *
       * Uses `orElse` to try parsing as MySQL first, then SQLite. The presence
       * of `mysql` or `sqlite` nested keys determines which variant is loaded.
       *
       * Example HOCON:
       *
       * {{{
       * database {
       *   mysql {
       *     host = "localhost"
       *     port = 3306
       *     database = "mydb"
       *     username = "user"
       *     password = "secret"
       *   }
       * }
       * }}}
       *
       * OR
       *
       * {{{
       * database {
       *   sqlite {
       *     path = "/data/app.db"
       *     in-memory = false
       *   }
       * }
       * }}}
       */
      implicit val config: Config[DatabaseConfig] =
        mysqlConfig.orElse(sqliteConfig).nested("database")
    }

    // Example usage
    object ExampleApp extends ZIOAppDefault {

      def run = {
        val mysqlExample = Map(
          "database.mysql.host"     -> "localhost",
          "database.mysql.port"     -> "3306",
          "database.mysql.database" -> "myapp",
          "database.mysql.username" -> "admin",
          "database.mysql.password" -> "secret123"
        )

        val sqliteExample = Map(
          "database.sqlite.path"      -> "/var/data/app.db",
          "database.sqlite.in-memory" -> "false"
        )

        for {
          mysql <- ZIO.withConfigProvider(ConfigProvider.fromMap(mysqlExample)) {
                     ZIO.config[DatabaseConfig]
                   }
          _ <- Console.printLine(s"MySQL config: $mysql")

          sqlite <-
            ZIO.withConfigProvider(ConfigProvider.fromMap(sqliteExample)) {
              ZIO.config[DatabaseConfig]
            }
          _ <- Console.printLine(s"SQLite config: $sqlite")
        } yield ()
      }
    }
  }
}
