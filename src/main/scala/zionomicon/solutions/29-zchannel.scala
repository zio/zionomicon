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
  package Range {}

  /**
   *   2. Try to implement the `ZSink.collectAll` sink by yourself using
   *      `ZChannel`:
   *
   * {{{
   * def collectAll[A]: ZSink[Any, Nothing, A, Nothing, Chunk[A]] =
   *   ???
   * }}}
   */
  package CollectAll {}

  /**
   *   3. Try to implement the `ZPipeline.dropWhile` pipeline using `ZChannel`:
   *
   * {{{
   * def dropWhile[A](f: A => Boolean): ZPipeline[Any, Nothing, A, A] =
   *   ???
   * }}}
   */
  package DropWhile {}

}
