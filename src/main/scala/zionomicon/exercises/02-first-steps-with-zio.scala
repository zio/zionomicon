package zionomicon.exercises

import zio._

object FirstStepsWithZIO {

  /**
   * Implement a ZIO version of the function `readFile` by using the
   * `ZIO.attempt` constructor.
   */
  object Exercise1 {

    def readFile(file: String): String = {
      val source = scala.io.Source.fromFile(file)

      try source.getLines.mkString
      finally source.close()
    }

    def readFileZio(file: String) =
      ???
  }

  /**
   * Implement a ZIO version of the function `writeFile` by using the
   * `ZIO.attempt` constructor.
   */
  object Exercise2 {

    def writeFile(file: String, text: String): Unit = {
      import java.io._
      val pw = new PrintWriter(new File(file))
      try pw.write(text)
      finally pw.close
    }

    def writeFileZio(file: String, text: String) =
      ???
  }

  /**
   * Using the `flatMap` method of ZIO effects, together with the `readFileZio`
   * and `writeFileZio` functions that you wrote, implement a ZIO version of
   * the function `copyFile`.
   */
  object Exercise3 {
    import Exercise1._
    import Exercise2._

    def copyFile(source: String, dest: String): Unit = {
      val contents = readFile(source)
      writeFile(dest, contents)
    }

    def copyFileZio(source: String, dest: String) =
      ???
  }

  /**
   * Rewrite the following ZIO code that uses `flatMap` into a
   * _for comprehension_.
   */
  object Exercise4 {

    def printLine(line: String) = ZIO.attempt(println(line))
    val readLine                = ZIO.attempt(scala.io.StdIn.readLine())

    printLine("What is your name?").flatMap { _ =>
      readLine.flatMap { name =>
        printLine(s"Hello, ${name}!")
      }
    }
  }

  /**
   * Rewrite the following ZIO code that uses `flatMap` into a
   * _for comprehension_.
   */
  object Exercise5 {

    val random                  = ZIO.attempt(scala.util.Random.nextInt(3) + 1)
    def printLine(line: String) = ZIO.attempt(println(line))
    val readLine                = ZIO.attempt(scala.io.StdIn.readLine())

    random.flatMap { int =>
      printLine("Guess a number from 1 to 3:").flatMap { _ =>
        readLine.flatMap { num =>
          if (num == int.toString) printLine("You guessed right!")
          else printLine(s"You guessed wrong, the number was $int!")
        }
      }
    }
  }

  /**
   * Implement the `zipWith` function in terms of the toy model of a ZIO
   * effect. The function should return an effect that sequentially composes
   * the specified effects, merging their results with the specified
   * user-defined function.
   */
  object Exercise6 {

    final case class ZIO[-R, +E, +A](run: R => Either[E, A])

    def zipWith[R, E, A, B, C](
      self: ZIO[R, E, A],
      that: ZIO[R, E, B]
    )(f: (A, B) => C): ZIO[R, E, C] =
      ???
  }

  /**
   * Implement the `collectAll` function in terms of the toy model of a ZIO
   * effect. The function should return an effect that sequentially collects
   * the results of the specified collection of effects.
   */
  object Exercise7 {
    import Exercise6._

    def collectAll[R, E, A](
      in: Iterable[ZIO[R, E, A]]
    ): ZIO[R, E, List[A]] =
      ???
  }

  /**
   * Implement the `foreach` function in terms of the toy model of a ZIO
   * effect. The function should return an effect that sequentially runs the
   * specified function on every element of the specified collection.
   */
  object Exercise8 {
    import Exercise6._

    def foreach[R, E, A, B](
      in: Iterable[A]
    )(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
      ???
  }

  /**
   * Implement the `orElse` function in terms of the toy model of a ZIO effect.
   * The function should return an effect that tries the left hand side, but if
   * that effect fails, it will fallback to the effect on the right hand side.
   */
  object Exercise9 {
    import Exercise6._

    def orElse[R, E1, E2, A](
      self: ZIO[R, E1, A],
      that: ZIO[R, E2, A]
    ): ZIO[R, E2, A] =
      ???
  }

  /**
   * Using the following code as a foundation, write a ZIO application that
   * prints out the contents of whatever files are passed into the program as
   * command-line arguments. You should use the function `readFileZio` that you
   * developed in these exercises, as well as `ZIO.foreach`.
   */
  object Exercise10 {

    import java.io.IOException

    object Cat extends ZIOAppDefault {
      
      val run =
        for {
          args <- ZIOAppArgs.getArgs
          _    <- cat(args)
        } yield ()

      def cat(files: Chunk[String]): ZIO[Any, IOException, Unit] =
        ???
    }
  }

  /**
   * Using `ZIO.fail` and `ZIO.succeed`, implement the following function,
   * which converts an `Either` into a ZIO effect:
   */
  object Exercise11 {

    def eitherToZIO[E, A](either: Either[E, A]): ZIO[Any, E, A] =
      ???
  }

  /**
   * Using `ZIO.fail` and `ZIO.succeed`, implement the following function,
   * which converts a `List` into a ZIO effect, by looking at the head element
   * in the list and ignoring the rest of the elements.
   */
  object Exercise12 {

    def listToZIO[A](list: List[A]): ZIO[Any, None.type, A] =
      ???
  }

  /**
   * Using `ZIO.succeed`, convert the following procedural function into a
   * ZIO function:
   */
  object Exercise13 {

    def currentTime(): Long = java.lang.System.currentTimeMillis()

    lazy val currentTimeZIO: ZIO[Any, Nothing, Long] =
      ???
  }

  /**
   * Using `ZIO.async`, convert the following asynchronous,
   * callback-based function into a ZIO function:
   */
  object Exercise14 {

    def getCacheValue(
      key: String,
      onSuccess: String => Unit,
      onFailure: Throwable => Unit
    ): Unit =
      ???

    def getCacheValueZio(key: String): ZIO[Any, Throwable, String] =
      ???
  }

  /**
   * Using `ZIO.async`, convert the following asynchronous,
   * callback-based function into a ZIO function:
   */
  object Exercise15 {

    trait User

    def saveUserRecord(
      user: User,
      onSuccess: () => Unit,
      onFailure: Throwable => Unit
    ): Unit =
      ???

    def saveUserRecordZio(user: User): ZIO[Any, Throwable, Unit] =
      ???
  }

  /**
   * Using `ZIO.fromFuture`, convert the following code to ZIO:
   */
  object Exercise16 {

    import scala.concurrent.{ExecutionContext, Future}
    trait Query
    trait Result

    def doQuery(query: Query)(implicit ec: ExecutionContext): Future[Result] =
      ???

    def doQueryZio(query: Query): ZIO[Any, Throwable, Result] =
      ???
  }

  /**
   * Using the `Console`, write a little program that asks the user what their
   * name is, and then prints it out to them with a greeting.
   */
  object Exercise17 {

    object HelloHuman extends ZIOAppDefault {
      val run =
        ???
    }
  }

  /**
   * Using the `Console` and `Random` services in ZIO, write a little program
   * that asks the user to guess a randomly chosen number between 1 and 3, and
   * prints out if they were correct or not.
   */
  object Exercise18 {

    object NumberGuessing extends ZIOAppDefault {
      val run =
        ???
    }
  }

  /**
   * Using the `Console` service and recursion, write a function that will
   * repeatedly read input from the console until the specified user-defined
   * function evaluates to `true` on the input.
   */
  object Exercise19 {

    import java.io.IOException

    def readUntil(
      acceptInput: String => Boolean
    ): ZIO[Console, IOException, String] =
      ???
  }

  /**
   * Using recursion, write a function that will continue evaluating the
   * specified effect, until the specified user-defined function evaluates to
   * `true` on the output of the effect.
   */
  object Exercise20 {

    def doUntil[R, E, A](
      body: ZIO[R, E, A]
    )(condition: A => Boolean): ZIO[R, E, A] =
      ???
  }
}
