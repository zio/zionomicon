package zionomicon.exercises

import zio._

object TheZIOErrorModel {

  /**
   * Using the appropriate effect constructor, fix the following function so
   * that it no longer fails with defects when executed. Make a note of how the
   * inferred return type for the function changes.
   */
  object Exercise1 {

    def failWithMessage(string: String) =
      ZIO.succeed(throw new Error(string))
  }

  /**
   * Using the `ZIO#foldCauseZIO` operator and the `Cause#defects` method,
   * implement the following function. This function should take the effect,
   * inspect defects, and if a suitable defect is found, it should recover from
   * the error with the help of the specified function, which generates a new
   * success value for such a defect.
   */
  object Exercise2 {

    def recoverFromSomeDefects[R, E, A](zio: ZIO[R, E, A])(
      f: Throwable => Option[A]
    ): ZIO[R, E, A] =
      ???
  }

  /**
   * Using the `ZIO#foldCauseZIO` operator and the `Cause#prettyPrint` method,
   * implement an operator that takes an effect, and returns a new effect that
   * logs any failures of the original effect (including errors and defects),
   * without changing its failure or success value.
   */
  object Exercise3 {

    def logFailures[R, E, A](zio: ZIO[R, E, A]): ZIO[R, E, A] =
      ???
  }

  /**
   * Using the `ZIO#exit` method, which "runs" an effect to an `Exit` value,
   * implement the following function, which will execute the specified effect
   * on any failure at all:
   */
  object Exercise4 {

    def onAnyFailure[R, E, A](
      zio: ZIO[R, E, A],
      handler: ZIO[R, E, Any]
    ): ZIO[R, E, A] =
      ???
  }

  /**
   * Using the `ZIO#refineOrDie` method, implement the `ioException` function,
   * which refines the error channel to only include the `IOException` error.
   */
  object Exercise5 {

    def ioException[R, A](
      zio: ZIO[R, Throwable, A]
    ): ZIO[R, java.io.IOException, A] =
      ???
  }

  /**
   * Using the `ZIO#refineToOrDie` method, narrow the error type of the
   * following effect to just `NumberFormatException`.
   */
  object Exercise6 {

    val parseNumber: ZIO[Any, Throwable, Int] =
      ZIO.attempt("foo".toInt)
  }

  /**
   * Using the `ZIO#foldZIO` method, implement the following two functions,
   * which make working with `Either` values easier, by shifting the unexpected
   * case into the error channel (and reversing this shifting).
   */
  object Exercise7 {

    def left[R, E, A, B](
      zio: ZIO[R, E, Either[A, B]]
    ): ZIO[R, Either[E, B], A] =
      ???

    def unleft[R, E, A, B](
      zio: ZIO[R, Either[E, B], A]
    ): ZIO[R, E, Either[A, B]] =
      ???
  }

  /**
   * Using the `ZIO#foldZIO` method, implement the following two functions,
   * which make working with `Either` values easier, by shifting the unexpected
   * case into the error channel (and reversing this shifting).
   */
  object Exercise8 {

    def right[R, E, A, B](
      zio: ZIO[R, E, Either[A, B]]
    ): ZIO[R, Either[E, A], B] =
      ???

    def unright[R, E, A, B](
      zio: ZIO[R, Either[E, A], B]
    ): ZIO[R, E, Either[A, B]] =
      ???
  }

  /**
   * Using the `ZIO#sandbox` method, implement the following function.
   */
  object Exercise9 {

    def catchAllCause[R, E1, E2, A](
      zio: ZIO[R, E1, A],
      handler: Cause[E1] => ZIO[R, E2, A]
    ): ZIO[R, E2, A] = ???
  }

  /**
   * Using the `ZIO#foldCauseZIO` method, implement the following function.
   */
  object Exercise10 {
    def catchAllCause[R, E1, E2, A](
      zio: ZIO[R, E1, A],
      handler: Cause[E1] => ZIO[R, E2, A]
    ): ZIO[R, E2, A] = ???
  }
}
