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
  package RewriteLegacySendData {}

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
