package zionomicon.solutions

package AppendixCFunctionalDesign {

  /**
   * Implement function composition by creating a function that combines two
   * functions into a single function.
   */
  package Exercise1 {
    object defs {
      def compose[A, B, C](f: B => C, g: A => B): A => C =
        a => f(g(a))
    }
  }

  /**
   * Implement a higher-order function that applies a function multiple times to
   * an initial value.
   */
  package Exercise2 {
    object defs {
      def repeat[A](f: A => A, n: Int)(initial: A): A = {
        var result = initial
        var i      = 0
        while (i < n) {
          result = f(result)
          i += 1
        }
        result
      }
    }
  }

  /**
   * Implement currying by converting a function that takes multiple arguments
   * into a sequence of functions that each take a single argument.
   */
  package Exercise3 {
    object defs {
      def curry[A, B, C](f: (A, B) => C): A => B => C =
        a => b => f(a, b)
    }
  }

  /**
   * Implement uncurrying by converting a curried function back into a function
   * that takes multiple arguments.
   */
  package Exercise4 {
    object defs {
      def uncurry[A, B, C](f: A => B => C): (A, B) => C =
        (a, b) => f(a)(b)
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
        b => f(a, b)
    }
  }

  /**
   * Implement a function that applies a list of functions sequentially to an
   * initial value, returning the final result.
   */
  package Exercise6 {
    object defs {
      def pipe[A](value: A, functions: List[A => A]): A =
        functions.foldLeft(value)((a, f) => f(a))
    }
  }

  /**
   * Implement a function that creates a new function by combining two functions
   * that return values of the same type (function coalgebra).
   */
  package Exercise7 {
    object defs {
      def fanout[A, B](f: A => B, g: A => B): A => (B, B) =
        a => (f(a), g(a))
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
        for {
          a <- effect1
          b <- effect2
        } yield f(a, b)
    }
  }

  /**
   * Implement a memoization function that caches the result of a pure function,
   * returning the cached result on subsequent calls with the same argument.
   */
  package Exercise9 {
    object defs {
      def memoize[A, B](f: A => B): A => B = {
        val cache = scala.collection.mutable.Map[A, B]()
        a => cache.getOrElseUpdate(a, f(a))
      }

      /**
       * Alternative memoization using ConcurrentHashMap for thread-safe
       * caching. This version is safe to use in multi-threaded environments.
       */
      def memoizeThreadSafe[A, B](f: A => B): A => B = {
        val cache = new java.util.concurrent.ConcurrentHashMap[A, B]()
        a => cache.computeIfAbsent(a, _ => f(a))
      }
    }

    object MemoizationDemo {
      def main(args: Array[String]): Unit = {
        println("=== Memoization Demo ===\n")

        // Non-memoized version
        println("1. Non-memoized factorial calls:")
        val factorial = (n: Int) => {
          println(s"   Computing factorial($n)...")
          (1 to n).foldLeft(1L)(_ * _)
        }

        val result1 = factorial(5)
        println(s"   Result: $result1")
        val result2 = factorial(5)
        println(s"   Result: $result2")
        println("   Notice: Function was called twice and recomputed\n")

        // Memoized version
        println("2. Memoized factorial calls:")
        val memoizedFactorial = defs.memoize { (n: Int) =>
          println(s"   Computing factorial($n)...")
          (1 to n).foldLeft(1L)(_ * _)
        }

        val result3 = memoizedFactorial(5)
        println(s"   Result: $result3")
        val result4 = memoizedFactorial(5)
        println(s"   Result: $result4")
        println("   Notice: Function computation only happened once!\n")

        // Multiple different arguments
        println("3. Memoized with different arguments:")
        val result5 = memoizedFactorial(6)
        println(s"   Result for 6: $result5")
        val result6 = memoizedFactorial(5)
        println(s"   Result for 5: $result6 (cached)")
        val result7 = memoizedFactorial(6)
        println(s"   Result for 6: $result7 (cached)")

        // Thread-safe memoization alternative
        println("\n4. Thread-safe memoization:")
        val threadSafeFactorial = defs.memoizeThreadSafe { (n: Int) =>
          println(s"   Computing factorial($n) [thread-safe]...")
          (1 to n).foldLeft(1L)(_ * _)
        }

        val result8 = threadSafeFactorial(5)
        println(s"   Result: $result8")
        val result9 = threadSafeFactorial(5)
        println(s"   Result: $result9 (cached, using ConcurrentHashMap)")

        println("\n=== Demo Complete ===")
      }
    }
  }

  /**
   * Implement a function that converts a function returning an Option into a
   * function that throws an exception if the option is None.
   */
  package Exercise10 {
    object defs {
      def optionToThrow[A, B](f: A => Option[B]): A => B =
        a =>
          f(a).getOrElse(
            throw new NoSuchElementException(
              s"optionToThrow: function returned None for input: $a"
            )
          )
    }
  }
}
