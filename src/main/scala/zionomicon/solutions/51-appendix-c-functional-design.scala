package zionomicon.solutions

import zio._

object AppendixCFunctionalDesign {

  /**
   * Implement function composition by creating a function that combines two
   * functions into a single function.
   */
  object Exercise1 {

    def compose[A, B, C](f: B => C, g: A => B): A => C =
      a => f(g(a))
  }

  /**
   * Implement a higher-order function that applies a function multiple times
   * to an initial value.
   */
  object Exercise2 {

    def repeat[A](f: A => A, n: Int)(initial: A): A = {
      var result = initial
      var i = 0
      while (i < n) {
        result = f(result)
        i += 1
      }
      result
    }
  }

  /**
   * Implement currying by converting a function that takes multiple arguments
   * into a sequence of functions that each take a single argument.
   */
  object Exercise3 {

    def curry[A, B, C](f: (A, B) => C): A => B => C =
      a => b => f(a, b)
  }

  /**
   * Implement uncurrying by converting a curried function back into a function
   * that takes multiple arguments.
   */
  object Exercise4 {

    def uncurry[A, B, C](f: A => B => C): (A, B) => C =
      (a, b) => f(a)(b)
  }

  /**
   * Implement partial application by creating a function that partially applies
   * arguments to a function, returning a new function that takes the remaining
   * arguments.
   */
  object Exercise5 {

    def partial[A, B, C](f: (A, B) => C, a: A): B => C =
      b => f(a, b)
  }

  /**
   * Implement a function that applies a list of functions sequentially to an
   * initial value, returning the final result.
   */
  object Exercise6 {

    def pipe[A](value: A, functions: List[A => A]): A =
      functions.foldLeft(value)((a, f) => f(a))
  }

  /**
   * Implement a function that creates a new function by combining two functions
   * that return values of the same type (function coalgebra).
   */
  object Exercise7 {

    def fanout[A, B](f: A => B, g: A => B): A => (B, B) =
      a => (f(a), g(a))
  }

  /**
   * Implement a function that lifts a binary operation into a ZIO effect,
   * allowing you to compose effectful computations using that operation.
   */
  object Exercise8 {

    def liftA2[R, E, A, B, C](
      f: (A, B) => C,
      effect1: ZIO[R, E, A],
      effect2: ZIO[R, E, B]
    ): ZIO[R, E, C] =
      for {
        a <- effect1
        b <- effect2
      } yield f(a, b)
  }

  /**
   * Implement a memoization function that caches the result of a pure function,
   * returning the cached result on subsequent calls with the same argument.
   */
  object Exercise9 {

    def memoize[A, B](f: A => B): A => B = {
      val cache = scala.collection.mutable.Map[A, B]()
      a => cache.getOrElseUpdate(a, f(a))
    }

    object MemoizationDemo extends ZIOAppDefault {

      val run = for {
        // Create an expensive function that computes factorial and prints when called
        _ <- ZIO.attempt(println("=== Memoization Demo ===\n"))

        // Non-memoized version
        _ <- ZIO.attempt(println("1. Non-memoized factorial calls:"))
        factorial = (n: Int) => {
          println(s"   Computing factorial($n)...")
          (1 to n).foldLeft(1L)(_ * _)
        }

        result1 <- ZIO.attempt(factorial(5))
        _ <- ZIO.attempt(println(s"   Result: $result1"))
        result2 <- ZIO.attempt(factorial(5))
        _ <- ZIO.attempt(println(s"   Result: $result2"))
        _ <- ZIO.attempt(println("   Notice: Function was called twice and recomputed\n"))

        // Memoized version
        _ <- ZIO.attempt(println("2. Memoized factorial calls:"))
        memoizedFactorial = memoize((n: Int) => {
          println(s"   Computing factorial($n)...")
          (1 to n).foldLeft(1L)(_ * _)
        })

        result3 <- ZIO.attempt(memoizedFactorial(5))
        _ <- ZIO.attempt(println(s"   Result: $result3"))
        result4 <- ZIO.attempt(memoizedFactorial(5))
        _ <- ZIO.attempt(println(s"   Result: $result4"))
        _ <- ZIO.attempt(println("   Notice: Function computation only happened once!\n"))

        // Multiple different arguments
        _ <- ZIO.attempt(println("3. Memoized with different arguments:"))
        result5 <- ZIO.attempt(memoizedFactorial(6))
        _ <- ZIO.attempt(println(s"   Result for 6: $result5"))
        result6 <- ZIO.attempt(memoizedFactorial(5))
        _ <- ZIO.attempt(println(s"   Result for 5: $result6 (cached)"))
        result7 <- ZIO.attempt(memoizedFactorial(6))
        _ <- ZIO.attempt(println(s"   Result for 6: $result7 (cached)"))
        _ <- ZIO.attempt(println("\n=== Demo Complete ==="))
      } yield ()
    }
  }

  /**
   * Implement a function that converts a function returning an Option into a
   * function that throws an exception if the option is None.
   */
  object Exercise10 {

    def optionToThrow[A, B](f: A => Option[B]): A => B =
      a => f(a).getOrElse(throw new NoSuchElementException())
  }

}
