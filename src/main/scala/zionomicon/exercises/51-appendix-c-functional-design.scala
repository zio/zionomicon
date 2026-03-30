package zionomicon.exercises

package AppendixCFunctionalDesign {
  /**
   * Implement function composition by creating a function that combines two
   * functions into a single function.
   */
  package Exercise1 {
    object defs {
      def compose[A, B, C](f: B => C, g: A => B): A => C =
        ???
    }
  }

  /**
   * Implement a higher-order function that applies a function multiple times to
   * an initial value.
   */
  package Exercise2 {
    object defs {
      def repeat[A](f: A => A, n: Int)(initial: A): A =
        ???
    }
  }

  /**
   * Implement currying by converting a function that takes multiple arguments
   * into a sequence of functions that each take a single argument.
   */
  package Exercise3 {
    object defs {
      def curry[A, B, C](f: (A, B) => C): A => B => C =
        ???
    }
  }

  /**
   * Implement uncurrying by converting a curried function back into a function
   * that takes multiple arguments.
   */
  package Exercise4 {
    object defs {
      def uncurry[A, B, C](f: A => B => C): (A, B) => C =
        ???
    }
  }

  /**
   * Implement partial application by creating a function that partially applies
   * arguments to a function, returning a new function that takes the remaining
   * arguments.
   */
  package Exercise5 {
    object defs {
      def partial[A, B, C](f: (A, B) => C, a: A): B => C =
        ???
    }
  }

  /**
   * Implement a function that applies a list of functions sequentially to an
   * initial value, returning the final result.
   */
  package Exercise6 {
    object defs {
      def pipe[A](value: A, functions: List[A => A]): A =
        ???
    }
  }

  /**
   * Implement a function that creates a new function by combining two functions
   * that return values of the same type (function coalgebra).
   */
  package Exercise7 {
    object defs {
      def fanout[A, B](f: A => B, g: A => B): A => (B, B) =
        ???
    }
  }

  /**
   * Implement a function that lifts a binary operation into a ZIO effect,
   * allowing you to compose effectful computations using that operation.
   */
  package Exercise8 {
    object defs {
      import zio._

      def liftA2[R, E, A, B, C](
        f: (A, B) => C,
        effect1: ZIO[R, E, A],
        effect2: ZIO[R, E, B]
      ): ZIO[R, E, C] =
        ???
    }
  }

  /**
   * Implement a memoization function that caches the result of a pure function,
   * returning the cached result on subsequent calls with the same argument.
   */
  package Exercise9 {
    object defs {
      def memoize[A, B](f: A => B): A => B =
        ???
    }
  }

  /**
   * Implement a function that converts a function returning an Option into a
   * function that throws an exception if the option is None.
   */
  package Exercise10 {
    object defs {
      def optionToThrow[A, B](f: A => Option[B]): A => B =
        ???
    }
  }
}
