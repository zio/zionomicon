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
  package AcquireReleaseWithImpl {}

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
  package SemaphoreImpl {}
}
