package zionomicon.solutions

package CommunicationProtocolsZIOHTTP {

  /**
   *   1. Modify a program that handles Books (with title and authors) to accept
   *      and return Protobuf messages instead of JSON data types. The endpoint
   *      definitions and route logic should remain unchanged - only the codec
   *      should be different.
   *
   * {{{
   * case class Book(title: String, authors: List[String])
   *
   * // Implement a way to use Protobuf codec instead of JsonCodec
   * // while keeping the same endpoint definitions and handlers
   * def protobufBookRoutes: Routes[Any, Response] = ???
   * }}}
   */
  package ProtobufEncoding {

    import zio._
    import zio.http._
    import zio.schema._
    import zio.schema.codec.ProtobufCodec._

    /**
     * Domain model for a Book with automatic schema derivation. The schema
     * enables both JSON and Protobuf codec support.
     */
    case class Book(
      title: String,
      authors: List[String]
    )

    object Book {
      implicit val schema: Schema[Book] = DeriveSchema.gen
    }

    /**
     * Mock repository for books. In a real application, this would interact
     * with a database.
     */
    trait BookRepo {
      def add(book: Book): ZIO[Any, Nothing, Unit]
      def find(title: String): ZIO[Any, Nothing, List[Book]]
    }

    /**
     * In-memory implementation of BookRepo for demonstration purposes.
     */
    object BookRepo {
      def inMemory: ZIO[Any, Nothing, BookRepo] =
        Ref.make(List.empty[Book]).map { ref =>
          new BookRepo {
            override def add(book: Book): ZIO[Any, Nothing, Unit] =
              ref.update(_ :+ book)

            override def find(query: String): ZIO[Any, Nothing, List[Book]] =
              ref.get.map { books =>
                books.filter(
                  _.title.toLowerCase.contains(query.toLowerCase)
                )
              }
          }
        }
    }

    package Solution {

      object ProtobufRoutes {

        /**
         * Creates routes that handle books using Protobuf encoding.
         *
         * KEY INSIGHT: This implementation is identical to the JSON version
         * from the chapter, except for ONE change: import
         * zio.schema.codec.ProtobufCodec._ (instead of JsonCodec._)
         *
         * The Body.from[A] and req.body.to[A] methods automatically use the
         * codec available in scope. By changing the import, we seamlessly
         * switch from JSON to Protobuf without changing any handler logic!
         */
        def protobufBookRoutes: zio.http.Routes[BookRepo, Response] =
          zio.http.Routes(
            /**
             * POST /books - Accept a book via Protobuf-encoded request body
             */
            Method.POST / "books" ->
              handler { (req: Request) =>
                req.body
                  .to[Book]
                  .mapBoth(
                    _ => Response.badRequest("Unable to deserialize Book"),
                    book =>
                      ZIO
                        .serviceWithZIO[BookRepo](_.add(book))
                        .as(Response.status(Status.Created))
                  )
                  .flatMap(identity)
              },
            /**
             * GET /books - Query books and return results as Protobuf-encoded
             * response
             */
            Method.GET / "books" ->
              handler { (req: Request) =>
                for {
                  query <-
                    ZIO
                      .fromOption(req.queryParam("q"))
                      .orElseFail(
                        Response.badRequest("Missing query parameter 'q'")
                      )
                  books <- ZIO.serviceWithZIO[BookRepo](_.find(query))
                } yield Response(
                  status = Status.Ok,
                  body = Body.from(books)
                )
              }
          )
      }

      /**
       * Example application demonstrating Protobuf-encoded book API.
       */
      object ExampleApp extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          for {
            repo  <- BookRepo.inMemory
            routes = ProtobufRoutes.protobufBookRoutes
            _ <- Server
                   .serve(routes)
                   .provide(
                     Server.default,
                     ZLayer.succeed(repo)
                   )
          } yield ()
      }

      /**
       * Integration tests for Protobuf-encoded routes using ZIO HTTP Client
       * API. Run with: sbtn "runMain
       * zionomicon.solutions.CommunicationProtocolsZIOHTTP.ProtobufEncoding.Solution.ProtobufRoutesTest"
       */
      object ProtobufRoutesTest extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          (for {
            repo  <- BookRepo.inMemory
            routes = ProtobufRoutes.protobufBookRoutes
            _     <- ZIO.debug("Starting Protobuf Routes Integration Tests...")
            _ <- Server
                   .serve(routes)
                   .provide(Server.default, ZLayer.succeed(repo))
                   .fork
            _ <- ZIO.sleep(1.second)

            _ <- ZIO.debug(
                   "\n=== TEST 1: POST /books - Add 'Programming in Scala' ==="
                 )
            url1 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/books")
                    )
            book1 = Book(
                      "Programming in Scala",
                      List("Martin Odersky", "Lex Spoon", "Bill Venners")
                    )
            req1  = Request.post(url1, Body.from(book1))
            res1 <- Client.batched(req1)
            _    <- ZIO.debug(s"Response: ${res1.status} (expected: 201 Created)")
            _ <- if (res1.status == Status.Created) {
                   ZIO.debug("✅ POST /books succeeded")
                 } else {
                   ZIO.fail(s"Unexpected status: ${res1.status}, expected: 201")
                 }

            _ <- ZIO.debug("\n=== TEST 2: GET /books?q=scala - Query books ===")
            url2 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/books?q=scala")
                    )
            req2    = Request.get(url2)
            res2   <- Client.batched(req2)
            _      <- ZIO.debug(s"Response: ${res2.status} (expected: 200 OK)")
            books2 <- res2.body.to[List[Book]]
            _      <- ZIO.debug(s"Found ${books2.length} book(s)")
            _ <- ZIO.foreach(books2) { book =>
                   ZIO.debug(s"  - ${book.title}")
                 }
            _ <- if (books2.nonEmpty) {
                   ZIO.debug("✅ GET /books?q=scala succeeded")
                 } else {
                   ZIO.fail("Expected to find books matching 'scala'")
                 }

            _ <- ZIO.debug(
                   "\n=== TEST 3: POST /books - Add 'Functional Programming in Scala' ==="
                 )
            url3 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/books")
                    )
            book3 = Book(
                      "Functional Programming in Scala",
                      List("Paul Chiusano", "Runar Bjarnason")
                    )
            req3  = Request.post(url3, Body.from(book3))
            res3 <- Client.batched(req3)
            _    <- ZIO.debug(s"Response: ${res3.status} (expected: 201 Created)")
            _ <- if (res3.status == Status.Created) {
                   ZIO.debug("✅ POST /books (second book) succeeded")
                 } else {
                   ZIO.fail(s"Unexpected status: ${res3.status}, expected: 201")
                 }

            _ <- ZIO.debug(
                   "\n=== TEST 4: GET /books?q=functional - Query with different keyword ==="
                 )
            url4 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/books?q=functional")
                    )
            req4    = Request.get(url4)
            res4   <- Client.batched(req4)
            _      <- ZIO.debug(s"Response: ${res4.status} (expected: 200 OK)")
            books4 <- res4.body.to[List[Book]]
            _ <- ZIO.debug(
                   s"Found ${books4.length} book(s) matching 'functional'"
                 )
            _ <- if (books4.length == 1) {
                   ZIO.debug("✅ GET /books?q=functional succeeded")
                 } else {
                   ZIO.fail(s"Expected 1 book, found ${books4.length}")
                 }

            _ <- ZIO.debug(
                   "\n=== TEST 5: GET /books - Missing query parameter ==="
                 )
            url5 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/books")
                    )
            req5  = Request.get(url5)
            res5 <- Client.batched(req5)
            _ <- ZIO.debug(
                   s"Response: ${res5.status} (expected: 400 Bad Request)"
                 )
            errorMsg <- res5.body.asString
            _        <- ZIO.debug(s"Error: $errorMsg")
            _ <- if (res5.status == Status.BadRequest) {
                   ZIO.debug(
                     "✅ Error handling works correctly for missing query parameter"
                   )
                 } else {
                   ZIO.fail(s"Unexpected status: ${res5.status}, expected: 400")
                 }

            _ <- ZIO.debug("\n✅ All tests completed successfully!")
          } yield ()).provide(Client.default)
      }

    }

  }

  /**
   *   2. Implement a HandlerAspect middleware that logs the processing time for
   *      each request in milliseconds. Similar to the duration protocol stack
   *      example, but using the HandlerAspect API with proper composition
   *      semantics.
   *
   * {{{
   * def requestDurationLogging: HandlerAspect[Any] = ???
   *
   * // Usage example:
   * // val route = (Method.GET / "hello") -> handler { ... } @@ requestDurationLogging
   * }}}
   */
  package RequestDurationLogging {

    import zio._
    import zio.http._

    package Solution {

      package RequestDurationLogging {

        object impl {

          // Internal header used to pass computed duration from
          // computeRequestDuration to logRequestDuration. Always removed before
          // the response reaches the HTTP client.
          private val DurationHeaderName = "x-internal-duration-ms"

          /**
           * First middleware layer: captures the start time on incoming and
           * computes the request duration on outgoing, storing it as an
           * internal response header for logRequestDuration to consume.
           *
           * State0 = java.time.Instant (start time) CtxOut = Unit
           *
           * DESIGN: This middleware is responsible for measurement. It captures
           * the exact instant the request enters and records elapsed time as a
           * response header. A downstream middleware (logRequestDuration) will
           * read this header to log the summary.
           */
          def computeRequestDuration: HandlerAspect[Any, Unit] =
            HandlerAspect.interceptHandlerStateful(
              Handler.fromFunctionZIO[Request] { request =>
                Clock.instant.map(startTime => (startTime, (request, ())))
              }
            )(
              Handler.fromFunctionZIO[(java.time.Instant, Response)] {
                case (startTime, response) =>
                  Clock.instant.map { endTime =>
                    val durationMs =
                      java.time.Duration.between(startTime, endTime).toMillis
                    response.addHeader(DurationHeaderName, durationMs.toString)
                  }
              }
            )

          /**
           * Second middleware layer: captures the request on incoming, then on
           * outgoing reads the duration header added by computeRequestDuration,
           * logs the summary line, and removes the internal header from the
           * response.
           *
           * Must be the outer layer (its outgoing runs after
           * computeRequestDuration.outgoing): routes @@ computeRequestDuration @@
           * logRequestDuration OR: logRequestDuration ++ computeRequestDuration
           *
           * State0 = Request (for method + URL in the log line) CtxOut = Unit
           *
           * DESIGN: This middleware is responsible for logging. It reads the
           * duration from the header computed by the inner middleware and
           * produces the structured log line. The internal header is removed so
           * clients never see it.
           */
          def logRequestDuration: HandlerAspect[Any, Unit] =
            HandlerAspect.interceptHandlerStateful(
              Handler.fromFunctionZIO[Request] { request =>
                ZIO.succeed((request, (request, ())))
              }
            )(
              Handler.fromFunctionZIO[(Request, Response)] {
                case (request, response) =>
                  response.headers.get(DurationHeaderName) match {
                    case Some(durationMs) =>
                      ZIO
                        .debug(
                          s"${request.method} ${request.url.encode} ${response.status.code} ${durationMs}ms"
                        )
                        .as(response.removeHeader(DurationHeaderName))
                    case None =>
                      ZIO
                        .debug(
                          s"${request.method} ${request.url.encode} ${response.status.code} (duration unavailable)"
                        )
                        .as(response)
                  }
              }
            )

          /**
           * Combined middleware for convenience. Equivalent to: routes @@
           * computeRequestDuration @@ logRequestDuration
           *
           * Uses ++ with reversed argument order (LIFO outgoing semantics):
           * logRequestDuration ++ computeRequestDuration ensures:
           * compute.outgoing (add header) runs before log.outgoing (read/remove
           * header)
           *
           * COMPOSABILITY: Users can also use computeRequestDuration and
           * logRequestDuration separately if they need to insert other
           * middleware between timing and logging.
           */
          def requestDurationLogging: HandlerAspect[Any, Unit] =
            logRequestDuration ++ computeRequestDuration
        }
      }

      /**
       * Example demonstrating the duration logging middleware in action.
       */
      object ExampleApp extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          for {
            _ <- Server
                   .serve(routes)
                   .provide(Server.default)
          } yield ()

        private val routes =
          Routes(
            Method.GET / "fast" ->
              handler {
                ZIO.succeed(Response.text("Quick response!"))
              },
            Method.GET / "slow" ->
              handler {
                for {
                  _ <- ZIO.sleep(500.millis)
                } yield Response.text("Slow response!")
              },
            Method.GET / "hello" ->
              handler { (_: Request) =>
                ZIO.succeed(Response.text("Hello, World!"))
              }
          ) @@ RequestDurationLogging.impl.requestDurationLogging
      }

      /**
       * Test application demonstrating duration logging with ZIO HTTP Client.
       * Run with: sbtn "runMain
       * zionomicon.solutions.CommunicationProtocolsZIOHTTP.RequestDurationLogging.Solution.DurationLoggingTest"
       */
      object DurationLoggingTest extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          (for {
            _ <- ZIO.debug("Starting Duration Logging Tests...")
            _ <- Server
                   .serve(testRoutes)
                   .provide(Server.default)
                   .fork
            _ <- ZIO.sleep(1.second)

            _ <- ZIO.debug("\n=== TEST 1: Fast endpoint ===")
            url1 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/fast")
                    )
            req1   = Request.get(url1)
            res1  <- Client.batched(req1)
            body1 <- res1.body.asString
            _     <- ZIO.debug(s"Response: $body1")

            _ <- ZIO.debug("\n=== TEST 2: Slow endpoint (500ms delay) ===")
            url2 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/slow")
                    )
            req2   = Request.get(url2)
            res2  <- Client.batched(req2)
            body2 <- res2.body.asString
            _     <- ZIO.debug(s"Response: $body2")

            _ <- ZIO.debug("\n=== TEST 3: Hello endpoint ===")
            url3 <- ZIO.fromEither(
                      URL.decode("http://localhost:8080/hello")
                    )
            req3   = Request.get(url3)
            res3  <- Client.batched(req3)
            body3 <- res3.body.asString
            _     <- ZIO.debug(s"Response: $body3")

            _ <-
              ZIO.debug("\n✅ Tests completed! Check logs above for duration.")
          } yield ()).provide(Client.default)

        private val testRoutes =
          Routes(
            Method.GET / "fast" ->
              handler {
                ZIO.succeed(Response.text("Quick response!"))
              },
            Method.GET / "slow" ->
              handler {
                for {
                  _ <- ZIO.sleep(500.millis)
                } yield Response.text("Slow response!")
              },
            Method.GET / "hello" ->
              handler { (_: Request) =>
                ZIO.succeed(Response.text("Hello, World!"))
              }
          ) @@ RequestDurationLogging.impl.requestDurationLogging
      }

      /**
       * COMPOSABILITY EXAMPLE
       *
       * The HandlerAspect composition semantics guarantee proper ordering:
       * Multiple middleware can be composed together with the @@ operator. Each
       * middleware wraps the previous one, creating a stack.
       *
       * Example: val route = handler { ... }
       * @@
       *   requestDurationLogging
       * @@
       *   someOtherMiddleware
       * @@
       *   anotherMiddleware
       *
       * If any middleware in the incoming pipeline fails (e.g., returns an
       * error), the response immediately goes to the outgoing pipeline, and all
       * middlewares still execute their outgoing handlers in reverse order.
       * This ensures proper cleanup and logging even when errors occur.
       */
    }

  }

  /**
   *   3. Write a server that serves static files from a specified directory.
   *      The server should accept a path parameter for the directory and serve
   *      files from that location. Return 404 Not Found if the directory or
   *      file doesn't exist.
   *
   * {{{
   * def staticFileServer(baseDir: String): Routes[Any, Response] = ???
   *
   * // Example usage:
   * // Server.serve(staticFileServer("/var/www")).provide(Server.default)
   *
   * // Example requests:
   * // GET /files/images/photo.jpg -> serves /var/www/files/images/photo.jpg
   * // GET /files/nonexistent -> returns 404 Not Found
   * }}}
   */
  package StaticFileServer {

    object Solution {}

  }

  /**
   *   4. Create an upload endpoint that accepts files in the request body and
   *      saves them to the server's file system. Use streaming to handle large
   *      files that may not fit in memory. The endpoint should validate file
   *      uploads and handle errors appropriately.
   *
   * {{{
   * sealed trait UploadError
   * object UploadError {
   *   case class InvalidFileName(msg: String) extends UploadError
   *   case class SaveError(msg: String) extends UploadError
   * }
   *
   * def uploadEndpoint(uploadDir: String): Route[Any, Response] = ???
   *
   * // Example usage:
   * // POST /upload with file content in body -> saves to uploadDir and returns 201 Created
   * // POST /upload with invalid filename -> returns 400 Bad Request
   * }}}
   */
  package FileUploadEndpoint {

    object Solution {}

  }

  /**
   *   5. Implement a rate limiting middleware that tracks requests by IP
   *      address and enforces a configurable request limit within a time
   *      window. When a client exceeds the limit, return 429 Too Many Requests.
   *
   * {{{
   * import zio._
   *
   * case class RateLimitConfig(
   *   maxRequests: Int,        // Maximum number of requests
   *   timeWindow: Duration     // Time window for the limit
   * )
   *
   * def rateLimitMiddleware(config: RateLimitConfig): HandlerAspect[Any] = ???
   *
   * // Usage example:
   * // val limiter = rateLimitMiddleware(RateLimitConfig(100, 1.minute))
   * // val route = (Method.GET / "api" / "data") -> handler { ... } @@ limiter
   *
   * // Expected behavior:
   * // First 100 requests within 1 minute -> 200 OK
   * // Request 101+ within the same minute -> 429 Too Many Requests
   * // After time window expires -> counter resets
   * }}}
   */
  package RateLimiting {

    object Solution {}

  }

}
