//> using scala 3.3.3
//> using dep "io.circe::circe-generic:0.14.13"
//> using dep "dev.hnaderi::named-codec-circe:0.3.0"
//> using dep "dev.hnaderi::lepus-std:0.5.5"
//> using dep "dev.hnaderi::lepus-circe:0.5.5"
//> using dep "dev.zio::zio-streams:2.1.19"
//> using dep "dev.zio::zio:2.1.19"
//> using dep "dev.zio::zio-interop-cats:23.1.0.5"

/**
 *   2. Write a ZIO program that uses lepus to connect to RabbitMQ server and
 *      publish arbitrary messages to a queue. Lepus is a purely functional
 *      scala client for RabbitMQ. You can find the library homepage
 *      [here](http://lepus.hnaderi.dev/).
 */

// To run this script:
// 1. Make sure RabbitMQ is running locally (or adjust the configuration)
//    docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
// 2. Run with scala-cli:
//    scala-cli run 02-lepus-integration.scala

import lepus.client.*
import lepus.protocol.domains.*
import zio.interop.catz.*
import zio.stream.*
import zio.stream.interop.fs2z.*
import zio.{Task, *}

object HelloWorld extends ZIOAppDefault {
  implicit lazy val taskConsole: cats.effect.std.Console[Task] =
    cats.effect.std.Console.make[Task]

  private val exchange = ExchangeName.default

  def app(con: Connection[Task]) = con.channel.use(ch =>
    for {
      _ <- ch.exchange.declare(ExchangeName("events"), ExchangeType.Topic)
      q <- ch.queue.declare(autoDelete = true)
      q <-
        ZIO.fromOption(q).orElseFail(new Exception("Queue declaration failed"))
      print =
        ch.messaging
          .consume[String](q.queue, mode = ConsumeMode.NackOnError)
          .toZStream()
          .tap { msg =>
            Console.printLine(s"received message: ${msg.message.payload}")
          }
      publish = ZStream
                  .fromZIO(Random.nextInt)
                  .repeat(Schedule.spaced(1.second))
                  .tap(l => Console.printLine(s"publishing $l"))
                  .map(l => Message[String](l.toString))
                  .mapZIO(msg => ch.messaging.publish(exchange, q.queue, msg))
      _ <- print.merge(publish).interruptAfter(10.seconds).runDrain
    } yield ()
  )

  override def run =
    for {
      conn <- LepusClient[Task](debug = true).toScopedZIO
      _    <- app(conn)
    } yield ()

}
