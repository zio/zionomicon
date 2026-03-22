package zionomicon.solutions

import zio._
import zio.stream._

package ZChannel {

  /**
   *   1. Use `ZChannel` to implement a function that takes two lower and upper
   *      bounds of the range and returns a stream that emits all the numbers in
   *      that range:
   *
   * {{{
   * def range(start: Int, end: Int): ZStream[Any, Nothing, Int] =
   *   ???
   * }}}
   */
  package Range {

    import zio.stream.ZChannel

    object Solution {

      def range(start: Int, end: Int): ZStream[Any, Nothing, Int] = {
        def loop(
          current: Int
        ): ZChannel[Any, Any, Any, Any, Nothing, Chunk[Int], Unit] =
          if (current > end)
            ZChannel.unit
          else
            ZChannel.write(Chunk.single(current)) *> loop(current + 1)

        ZStream.fromChannel(loop(start))
      }
    }

    // --- Example Showcase ---

    object Exercise1Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <-
          Console.printLine("=== Exercise 1: ZChannel-based range stream ===")
        // Range 1 to 10
        _       <- Console.printLine("\n--- range(1, 10) ---")
        result1 <- Solution.range(1, 10).runCollect
        _       <- Console.printLine(s"  ${result1.mkString(", ")}")
        // Empty range
        _       <- Console.printLine("\n--- range(5, 3) (empty) ---")
        result2 <- Solution.range(5, 3).runCollect
        _       <- Console.printLine(s"  ${result2.mkString(", ")}")
        // Single element
        _       <- Console.printLine("\n--- range(42, 42) (single element) ---")
        result3 <- Solution.range(42, 42).runCollect
        _       <- Console.printLine(s"  ${result3.mkString(", ")}")
        // Negative range
        _       <- Console.printLine("\n--- range(-3, 3) (negative to positive) ---")
        result4 <- Solution.range(-3, 3).runCollect
        _       <- Console.printLine(s"  ${result4.mkString(", ")}")
      } yield ()
    }
  }

  /**
   *   2. Try to implement the `ZSink.collectAll` sink by yourself using
   *      `ZChannel`:
   *
   * {{{
   * def collectAll[A]: ZSink[Any, Nothing, A, Nothing, Chunk[A]] =
   *   ???
   * }}}
   */
  package CollectAll {

    import zio.stream.ZChannel

    object Solution {

      def collectAll[A]: ZSink[Any, Nothing, A, Nothing, Chunk[A]] = {
        def loop(
          acc: Chunk[A]
        ): ZChannel[Any, ZNothing, Chunk[A], Any, Nothing, Chunk[
          Nothing
        ], Chunk[A]] =
          ZChannel.readWithCause(
            (chunk: Chunk[A]) => loop(acc ++ chunk),
            (cause: Cause[ZNothing]) => ZChannel.refailCause(cause),
            (_: Any) => ZChannel.succeed(acc)
          )

        ZSink.fromChannel(loop(Chunk.empty))
      }
    }

    // --- Example Showcase ---

    object Exercise2Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine(
               "=== Exercise 2: ZChannel-based collectAll sink ==="
             )
        // Collect integers
        _       <- Console.printLine("\n--- Collect stream of 1 to 5 ---")
        result1 <- ZStream(1, 2, 3, 4, 5).run(Solution.collectAll)
        _       <- Console.printLine(s"  $result1")
        // Empty stream
        _       <- Console.printLine("\n--- Collect empty stream ---")
        result2 <- ZStream.empty.run(Solution.collectAll[Int])
        _       <- Console.printLine(s"  $result2")
      } yield ()
    }
  }

  /**
   *   3. Try to implement the `ZPipeline.dropWhile` pipeline using `ZChannel`:
   *
   * {{{
   * def dropWhile[A](f: A => Boolean): ZPipeline[Any, Nothing, A, A] =
   *   ???
   * }}}
   */
  package DropWhile {

    import zio.stream.ZChannel

    object Solution {

      def dropWhile[A](f: A => Boolean): ZPipeline[Any, Nothing, A, A] = {
        // Phase 1: Drop elements matching the predicate
        def dropping
          : ZChannel[Any, ZNothing, Chunk[A], Any, Nothing, Chunk[A], Any] =
          ZChannel.readWithCause(
            (chunk: Chunk[A]) => {
              val remaining = chunk.dropWhile(f)
              if (remaining.isEmpty)
                dropping // Entire chunk dropped, keep dropping
              else
                ZChannel.write(
                  remaining
                ) *> passthrough // Found non-matching, switch
            },
            (cause: Cause[ZNothing]) => ZChannel.refailCause(cause),
            (done: Any) => ZChannel.succeed(done)
          )

        // Phase 2: Pass all chunks through unchanged
        def passthrough
          : ZChannel[Any, ZNothing, Chunk[A], Any, Nothing, Chunk[A], Any] =
          ZChannel.readWithCause(
            (chunk: Chunk[A]) => ZChannel.write(chunk) *> passthrough,
            (cause: Cause[ZNothing]) => ZChannel.refailCause(cause),
            (done: Any) => ZChannel.succeed(done)
          )

        ZPipeline.fromChannel(dropping)
      }
    }

    // --- Example Showcase ---

    object Exercise3Example extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine(
               "=== Exercise 3: ZChannel-based dropWhile pipeline ==="
             )
        // Drop while less than 5
        _ <- Console.printLine("\n--- Drop while < 5 from (1 to 8) ---")
        r1 <- ZStream(1, 2, 3, 4, 5, 6, 7, 8)
                .via(Solution.dropWhile(_ < 5))
                .runCollect
        _ <- Console.printLine(s"  ${r1.mkString(", ")}")
        // Compare with built-in ZPipeline.dropWhile
        _ <- Console.printLine("\n--- Compare with ZPipeline.dropWhile ---")
        builtin <- ZStream(2, 4, 6, 3, 8, 10)
                     .via(ZPipeline.dropWhile[Int](_ % 2 == 0))
                     .runCollect
        custom <- ZStream(2, 4, 6, 3, 8, 10)
                    .via(Solution.dropWhile(_ % 2 == 0))
                    .runCollect
        _ <- Console.printLine(s"  Built-in: ${builtin.mkString(", ")}")
        _ <- Console.printLine(s"  Custom:   ${custom.mkString(", ")}")
        _ <- Console.printLine(s"  Equal:    ${builtin == custom}")
      } yield ()
    }
  }

}
