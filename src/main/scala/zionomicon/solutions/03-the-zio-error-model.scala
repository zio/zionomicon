package zionomicon.solutions

import zio._

object TheZIOErrorModel {

  /**
   *   1. Using the appropriate effect constructor, fix the following function
   *      so that it no longer fails with defects when executed. Make a note of
   *      how the inferred return type for the function changes.
   */
  object Exercise1 {

    def failWithMessage(string: String): ZIO[Any, Throwable, Nothing] =
      ZIO.attempt(throw new Error(string))
  }

  /**
   *   2. Using the `ZIO#foldCauseZIO` operator and the `Cause#defects` method,
   *      implement the following function. This function should take the
   *      effect, inspect defects, and if a suitable defect is found, it should
   *      recover from the error with the help of the specified function, which
   *      generates a new success value for such a defect.
   */
  object Exercise2 {

    def recoverFromSomeDefects[R, E, A](zio: ZIO[R, E, A])(
      f: Throwable => Option[A]
    ): ZIO[R, E, A] =
      zio.foldCauseZIO(
        cause =>
          cause.defects
            .collectFirst(Function.unlift(f))
            .fold[ZIO[R, E, A]](ZIO.failCause(cause))(a => ZIO.succeed(a)),
        a => ZIO.succeed(a)
      )
  }

  /**
   *   3. Using the `ZIO#foldCauseZIO` operator and the `Cause#prettyPrint`
   *      method, implement an operator that takes an effect, and returns a new
   *      effect that logs any failures of the original effect (including errors
   *      and defects), without changing its failure or success value.
   */
  object Exercise3 {

    def logFailures[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      zio.foldCauseZIO(
        cause =>
          ZIO.succeed(println(cause.prettyPrint)) *> ZIO.failCause(cause),
        a => ZIO.succeed(a)
      )
  }

  /**
   *   4. Using the `ZIO#exit` method, which "runs" an effect to an `Exit`
   *      value, implement the following function, which will execute the
   *      specified effect on any failure at all:
   */
  object Exercise4 {

    def onAnyFailure[R, E, A](
      zio: ZIO[R, E, A],
      handler: ZIO[R, E, Any]
    ): ZIO[R, E, A] =
      zio.exit.flatMap {
        case Exit.Failure(cause) => handler *> ZIO.failCause(cause)
        case Exit.Success(a)     => ZIO.succeed(a)
      }
  }

  /**
   *   5. Using the `ZIO#refineOrDie` method, implement the `ioException`
   *      function, which refines the error channel to only include the
   *      `IOException` error.
   */
  object Exercise5 {

    def ioException[R, A](
      zio: ZIO[R, Throwable, A]
    ): ZIO[R, java.io.IOException, A] =
      zio.refineToOrDie[java.io.IOException]
  }

  /**
   *   6. Using the `ZIO#refineToOrDie` method, narrow the error type of the
   *      following effect to just `NumberFormatException`.
   */
  object Exercise6 {

    val parseNumber: ZIO[Any, Throwable, Int] =
      ZIO.attempt("foo".toInt).refineToOrDie[NumberFormatException]
  }

  /**
   *   7. Using the `ZIO#foldZIO` method, implement the following two functions,
   *      which make working with `Either` values easier, by shifting the
   *      unexpected case into the error channel (and reversing this shifting).
   */
  object Exercise7 {

    def left[R, E, A, B](
      zio: ZIO[R, E, Either[A, B]]
    ): ZIO[R, Either[E, B], A] =
      zio.foldZIO(
        e => ZIO.fail(Left(e)),
        _.fold(a => ZIO.succeed(a), b => ZIO.fail(Right(b)))
      )

    def unleft[R, E, A, B](
      zio: ZIO[R, Either[E, B], A]
    ): ZIO[R, E, Either[A, B]] =
      zio.foldZIO(
        _.fold(e => ZIO.fail(e), b => ZIO.succeed(Right(b))),
        a => ZIO.succeed(Left(a))
      )
  }

  /**
   *   8. Using the `ZIO#foldZIO` method, implement the following two functions,
   *      which make working with `Either` values easier, by shifting the
   *      unexpected case into the error channel (and reversing this shifting).
   */
  object Exercise8 {

    def right[R, E, A, B](
      zio: ZIO[R, E, Either[A, B]]
    ): ZIO[R, Either[E, A], B] =
      zio.foldZIO(
        e => ZIO.fail(Left(e)),
        _.fold(a => ZIO.fail(Right(a)), b => ZIO.succeed(b))
      )

    def unright[R, E, A, B](
      zio: ZIO[R, Either[E, A], B]
    ): ZIO[R, E, Either[A, B]] =
      zio.foldZIO(
        _.fold(e => ZIO.fail(e), a => ZIO.succeed(Left(a))),
        b => ZIO.succeed(Right(b))
      )
  }

  /**
   *   9. Using the `ZIO#sandbox` method, implement the following function.
   */
  object Exercise9 {

    def catchAllCause[R, E1, E2, A](
      zio: ZIO[R, E1, A],
      handler: Cause[E1] => ZIO[R, E2, A]
    ): ZIO[R, E2, A] =
      zio.sandbox.foldZIO(cause => handler(cause), a => ZIO.succeed(a))
  }

  /**
   *   10. Using the `ZIO#foldCauseZIO` method, implement the following
   *       function.
   */
  object Exercise10 {
    def catchAllCause[R, E1, E2, A](
      zio: ZIO[R, E1, A],
      handler: Cause[E1] => ZIO[R, E2, A]
    ): ZIO[R, E2, A] =
      zio.foldCauseZIO(cause => handler(cause), a => ZIO.succeed(a))
  }
}
