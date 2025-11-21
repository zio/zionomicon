package zionomicon.exercises

package ResourceHanlding {

  /**
   *   1. Rewrite the following `sendData` function in terms of the
   *      `ZIO.requireReleaseWith` operator:
   *
   * {{{
   * import zio._
   * import scala.util.Try
   * import java.net.Socket
   *
   * object LegacySendData {
   *   def sendData(
   *     host: String,
   *     port: Int,
   *     data: Array[Byte]
   *   ): Try[Int] = {
   *     var socket: Socket = null
   *     try {
   *       socket = new Socket(host, port)
   *       val out = socket.getOutputStream
   *       out.write(data)
   *       Success(data.length)
   *     } catch {
   *       case e: Exception => Failure(e)
   *     } finally {
   *       if (socket != null) socket.close()
   *     }
   *   }
   * }
   * }}}
   *
   * Rewrite the function using ZIO
   *
   * {{{
   * def sendData(
   *   host: String,
   *   port: Int,
   *   data: Array[Byte]
   * ): Task[Int] = ???
   * }}}
   */
  package RewriteLegacySendData {
    import zio._

    import java.net.{ServerSocket, Socket}

    object SocketClient extends ZIOAppDefault {
      def sendData(
        host: String,
        port: Int,
        data: Array[Byte]
      ): Task[Int] =
        ZIO.acquireReleaseWith(
          ZIO.attempt(new Socket(host, port))
        )(socket => ZIO.succeed(socket.close()))(socket =>
          ZIO.attempt {
            val out = socket.getOutputStream
            out.write(data)
            data.length
          }
        )

      def run: Task[Unit] = {
        val messages = List(
          "Hello, Server!",
          "This is message 2",
          "ZIO rocks!",
          "Final message"
        )

        ZIO.foreachParDiscard(messages) { message =>
          for {
            bytesSent <- sendData("localhost", 8080, message.getBytes("UTF-8"))
            _         <- Console.printLine(s"✓ Sent: '$message' ($bytesSent bytes)")
            _         <- ZIO.sleep(1.second)
          } yield ()
        }
      }
    }

    object SocketServer extends ZIOAppDefault {
      def readData(socket: Socket): Task[Array[Byte]] =
        ZIO.acquireReleaseWith(
          ZIO.attempt(socket.getInputStream)
        )(_ => ZIO.succeed(()))(inputStream =>
          ZIO.attempt(inputStream.readAllBytes())
        )

      def handleClient(clientSocket: Socket, clientId: Int): Task[Unit] =
        ZIO
          .acquireReleaseWith(
            ZIO.succeed(clientSocket)
          )(socket => ZIO.attempt(socket.close()).orDie)(socket =>
            for {
              _ <-
                Console.printLine(
                  s"[Client $clientId] Connected from ${socket.getRemoteSocketAddress}"
                )
              data   <- readData(socket)
              message = new String(data, "UTF-8")
              _ <-
                Console.printLine(
                  s"[Client $clientId] Received ${data.length} bytes: '$message'"
                )
              _ <- Console.printLine(s"[Client $clientId] Connection closed")
            } yield ()
          )
          .catchAll { error =>
            Console.printLine(s"[Client $clientId] Error: ${error.getMessage}")
          }

      def acceptLoop(
        serverSocket: ServerSocket,
        clientCounter: Ref[Int]
      ): Task[Unit] = {
        for {
          clientSocket <- ZIO.attempt(serverSocket.accept())
          clientId     <- clientCounter.updateAndGet(_ + 1)
          _            <- handleClient(clientSocket, clientId)
        } yield ()
      }.forever

      def run: Task[Unit] = {
        val port = 8080

        ZIO.acquireReleaseWith(
          ZIO.attempt(new ServerSocket(port))
        )(server => ZIO.attempt(server.close()).orDie)(server =>
          for {
            _             <- Console.printLine(s"Server listening on port $port...")
            clientCounter <- Ref.make(0)
            _             <- acceptLoop(server, clientCounter)
          } yield ()
        )
      }
    }

  }

  /**
   *   2. Implement `ZIO.acquireReleaseWith` using `ZIO.uninterruptibleMask`.
   *      Write a test to ensure that `ZIO.acquireReleaseWith` guarantees the
   *      three rules discussed in this chapter.
   */
  package AcquireReleaseWithImpl {

    import zio._
    import zio.test._

    /**
     * Implementation of ZIO.acquireReleaseWith using ZIO.uninterruptibleMask
     */
    object AcquireReleaseImpl {

      def acquireReleaseWith[R, E, A, B](
        acquire: ZIO[R, E, A]
      )(
        release: A => ZIO[R, Nothing, Any]
      )(use: A => ZIO[R, E, B]): ZIO[R, E, B] =
        ZIO.uninterruptibleMask { restore =>
          // Step 1: Run acquire uninterruptibly (we're already in uninterruptible region)
          acquire.flatMap { resource =>
            // Step 2: Restore interruptibility for the use action and capture its exit result
            restore(use(resource)).exit.flatMap { exit =>
              // Step 3: Run release uninterruptibly, then complete with the original exit result
              release(resource).uninterruptible *> ZIO.suspendSucceed(exit)
            }
          }
        }
    }

    /**
     * Comprehensive test suite to verify the three guarantees:
     *   1. The `acquire` action will be performed uninterruptibly.
     *   2. The `release` action will be performed uninterruptibly.
     *   3. If the `acquire` action successfully completes execution, then the
     *      `release` action will be performed as soon as the `use` action
     *      completes execution, regardless of how `use` completes execution.
     */
    object AcquireReleaseWithSpec extends ZIOSpecDefault {
      import AcquireReleaseImpl._

      def spec = suite("AcquireReleaseWith Implementation")(
        // Test 1: Acquire is uninterruptible
        test("acquire runs uninterruptibly even when interrupted") {
          for {
            acquireInterrupted <- Ref.make(false)
            latch1             <- Promise.make[Nothing, Unit]
            latch2             <- Promise.make[Nothing, Unit]

            acquire =
              (latch1.succeed(()) *> latch2.await)
                .map(_ => "resource")
                .onInterrupt(
                  acquireInterrupted.set(true)
                )

            release = (_: String) => ZIO.unit
            use     = (_: String) => ZIO.unit

            fiber <- acquireReleaseWith(acquire)(release)(use).fork
            _     <- latch1.await *> fiber.interrupt *> latch2.succeed(())
            _     <- fiber.join

            acquireInterrupted <- acquireInterrupted.get
          } yield assertTrue(!acquireInterrupted)
        },

        // Test 2: Release is uninterruptible
        test("release runs uninterruptibly even when interrupted") {
          for {
            latch1             <- Promise.make[Nothing, Unit]
            latch2             <- Promise.make[Nothing, Unit]
            releaseInterrupted <- Ref.make(false)

            acquire = ZIO.succeed("resource")
            release = (_: String) =>
                        (latch1.succeed(()) *> latch2.await)
                          .onInterrupt(releaseInterrupted.set(true))
            use = (_: String) => ZIO.unit

            fiber              <- acquireReleaseWith(acquire)(release)(use).fork
            _                  <- latch1.await *> fiber.interrupt *> latch2.succeed(())
            _                  <- fiber.join
            releaseInterrupted <- releaseInterrupted.get
          } yield assertTrue(!releaseInterrupted)
        },

        // Test 3a: Release is called when use succeeds
        test("release is called when use completes successfully") {
          for {
            released <- Ref.make(false)

            acquire = ZIO.succeed("resource")
            release = (_: String) => released.set(true)
            use     = (_: String) => ZIO.succeed(42)

            result      <- acquireReleaseWith(acquire)(release)(use)
            wasReleased <- released.get
          } yield assertTrue(
            result == 42,
            wasReleased
          )
        },

        // Test 3b: Release is called when use fails
        test("release is called when use fails") {
          for {
            released <- Ref.make(false)

            acquire = ZIO.succeed("resource")
            release = (_: String) => released.set(true)
            use     = (_: String) => ZIO.fail("error")

            result      <- acquireReleaseWith(acquire)(release)(use).exit
            wasReleased <- released.get
          } yield assertTrue(
            result.isFailure,
            wasReleased
          )
        },

        // Test 3c: Release is called when use is interrupted
        test("release is called when use is interrupted") {
          for {
            released   <- Ref.make(false)
            useStarted <- Promise.make[Nothing, Unit]

            acquire = ZIO.succeed("resource")
            release = (_: String) => released.set(true)
            use     = (_: String) => useStarted.succeed(()) *> ZIO.never

            fiber <- acquireReleaseWith(acquire)(release)(use).fork
            _     <- useStarted.await *> fiber.interrupt

            wasReleased <- released.get
          } yield assertTrue(wasReleased)
        },

        // Test 4: If acquire fails, release is not called
        test("release is not called if acquire fails") {
          for {
            released <- Ref.make(false)

            acquire = ZIO.fail("acquire failed")
            release = (_: String) => released.set(true)
            use     = (_: String) => ZIO.succeed(())

            result      <- acquireReleaseWith(acquire)(release)(use).exit
            wasReleased <- released.get
          } yield assertTrue(
            result.isFailure,
            !wasReleased
          )
        },

        // Test 5: Use action can be interruptible
        test("use action is interruptible by default") {
          for {
            useInterrupted <- Ref.make(false)
            released       <- Ref.make(false)
            latch          <- Promise.make[Nothing, Unit]

            acquire = ZIO.succeed("resource")
            release = (_: String) => released.set(true)
            use = (_: String) =>
                    (latch.succeed(()) *> ZIO.never)
                      .onInterrupt(useInterrupted.set(true))

            fiber <- acquireReleaseWith(acquire)(release)(use).fork
            _     <- latch.await *> fiber.interrupt

            interrupted <- useInterrupted.get
            wasReleased <- released.get
          } yield assertTrue(
            interrupted, // Use was interrupted
            wasReleased  // But the release still ran
          )
        }
      )
    }

  }

  /**
   *   3. Implement a simple semaphore using `Ref` and `Promise` and using
   *      `ZIO.acquireReleaseWith` operator. A semaphore is a synchronization
   *      primitive controlling access to a common resource by multiple fibers.
   *      It is essentially a counter that tracks how many fibers can access a
   *      resource at a time:
   *
   * {{{
   * trait Semaphore {
   *   def withPermits[R, E, A](n: Long)(task: ZIO[R, E, A]): ZIO[R, E, A]
   * }
   *
   * object Semaphore {
   *   def make(permits: => Long): UIO[Semaphore] = ???
   * }
   * }}}
   */
  package SemaphoreImpl {

    import zio._
    import scala.collection.immutable.Queue

    /**
     * A semaphore is a synchronization primitive that controls access to a
     * common resource by multiple fibers using a counter that tracks available
     * permits.
     */
    trait Semaphore {

      /**
       * Executes a task with the specified number of permits. Acquires n
       * permits before running the task and releases them afterwards, even if
       * the task fails or is interrupted.
       *
       * @param n
       *   the number of permits to acquire
       * @param task
       *   the task to execute with the acquired permits
       */
      def withPermits[R, E, A](n: Long)(task: ZIO[R, E, A]): ZIO[R, E, A]
    }

    object Semaphore {

      /**
       * Internal state of the semaphore.
       *
       * @param permits
       *   the number of currently available permits
       * @param waiting
       *   queue of fibers waiting for permits, each with their required permit
       *   count and a promise to complete when ready
       */
      private case class State(
        permits: Long,
        waiting: Queue[(Long, Promise[Nothing, Unit])]
      )

      /**
       * Creates a new semaphore with the specified number of permits.
       *
       * @param permits
       *   the initial number of permits (must be non-negative)
       * @return
       *   a new Semaphore instance
       */
      def make(permits: => Long): UIO[Semaphore] =
        Ref.make(State(permits, Queue.empty)).map { ref =>
          new Semaphore {

            /**
             * Acquires n permits from the semaphore. If not enough permits are
             * available, the fiber will wait.
             */
            private def acquire(n: Long): UIO[Unit] =
              ZIO.suspendSucceed {
                if (n <= 0) {
                  // No permits needed, proceed immediately
                  ZIO.unit
                } else {
                  Promise.make[Nothing, Unit].flatMap { promise =>
                    ref.modify { state =>
                      if (state.permits >= n) {
                        // Enough permits available, acquire them immediately
                        (
                          ZIO.unit,
                          state.copy(permits = state.permits - n)
                        )
                      } else {
                        // Not enough permits, add to waiting queue
                        (
                          promise.await,
                          state
                            .copy(waiting = state.waiting.enqueue((n, promise)))
                        )
                      }
                    }.flatten
                  }
                }
              }

            /**
             * Releases n permits back to the semaphore. This may wake up
             * waiting fibers if they can now proceed.
             */
            private def release(n: Long): UIO[Unit] = {

              def satisfyWaiters(state: State): (UIO[Unit], State) =
                state.waiting.dequeueOption match {
                  case Some(((needed, promise), rest))
                      if state.permits >= needed =>
                    // This waiter can proceed - they have enough permits
                    val newState = state.copy(
                      permits = state.permits - needed,
                      waiting = rest
                    )
                    // Recursively check if we can satisfy more waiters
                    val (moreEffects, finalState) = satisfyWaiters(newState)
                    (
                      promise.succeed(()) *> moreEffects,
                      finalState
                    )
                  case _ =>
                    // Either queue is empty or next waiter needs more permits
                    (ZIO.unit, state)
                }

              ZIO.suspendSucceed {
                if (n <= 0) {
                  ZIO.unit
                } else {
                  ref.modify { state =>
                    // First, add the released permits back
                    val stateWithPermits =
                      state.copy(permits = state.permits + n)
                    // Then try to satisfy any waiting fibers
                    satisfyWaiters(stateWithPermits)
                  }.flatten
                }
              }
            }

            /**
             * Executes a task with n permits using acquireReleaseWith to ensure
             * permits are always released, even on failure or interruption.
             */
            def withPermits[R, E, A](
              n: Long
            )(task: ZIO[R, E, A]): ZIO[R, E, A] =
              ZIO.acquireReleaseWith(acquire(n))(_ => release(n))(_ => task)
          }
        }
    }

  }
}
