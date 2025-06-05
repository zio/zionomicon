package zionomicon.exercises

package zionomicon.exercises

import zio._
import java.net.URL

object ConcurrencyOperators {

    /* 
        1. Implement the collectAllPar combinator using foreachPar.
    */

    def foreachPar[R, E, A, B](
        in: Iterable[A]
    )(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] = ???

    /* 
        2. Write a function that takes a collection of ZIO effects and collects all the successful
           and failed results as a tuple.
    */

      def collectAllPar[R, E, A](
        in: Iterable[ZIO[R, E, A]]
    ): ZIO[R, E, List[A]] = ???

    /* 
       3. Assume you have given the following fetchUrl function that fetches a URL and
          returns a ZIO effect:
    */
    def fetchUrl(url: URL): ZIO[Any, Throwable, String] = ???

     def fetchAllUrlsPar(
        urls: List[String]
    ): ZIO[Any, Nothing, (List[(URL, Throwable)], List[(URL, String)])] = ???

}
