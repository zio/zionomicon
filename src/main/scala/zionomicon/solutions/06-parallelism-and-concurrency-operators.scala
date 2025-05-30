package zionomicon.solutions

package ParallelismAndConcurrencyOperators {
  import zio._

  import java.net.URI
  import scala.collection.compat.toTraversableLikeExtensionMethods

  /**
   *   1. Implement the `collectAllPar` combinator using `foreachPar`.
   */
  object CollectAllParImpl {
    def collectAllPar[R, E, A](
      in: Iterable[ZIO[R, E, A]]
    ): ZIO[R, E, List[A]] =
      ZIO.foreachPar(in)(identity).map(_.toList)
  }

  /**
   *   2. Write a function that takes a collection of `ZIO` effects and collects
   *      all the successful and failed results as a tuple.
   *
   * Hint: Use `ZIO.validatePar` or `ZIO.partitionPar`.
   */
  object CollectAllParResultImpl {
    def collectAllParResults[R, E, A](
      in: Iterable[ZIO[R, E, A]]
    ): ZIO[R, Nothing, (List[A], List[E])] =
      ZIO.partitionPar(in)(identity).map { case (failures, successes) =>
        (successes.toList, failures.toList)
      }
  }

  /**
   *   3. Assume you have given the following `fetchUrl` function that fetches a
   *      URL and returns a `ZIO` effect:
   *
   * ```scala
   * def fetchUrl(url: URL): ZIO[Any, Throwable, String] = ???
   * ```
   *
   * And you have a list of URLs you want to fetch in parallel. Implement a
   * function that fetches all the URLs in parallel and collects both the
   * successful and failed results. Both successful and failed results should be
   * paired with the URL they correspond to.
   *
   * ```scala
   * def fetchAllUrlsPar(
   *   urls: List[String]
   * ): ZIO[Any, Nothing, (List[(URL, Throwable)], List[(URL, String)])] =
   *   ???
   * ```
   */
  object FetchAllUrlsParImpl {
    import zio._
    import java.net.URL

    def fetchUrl(url: URL): ZIO[Any, Throwable, String] = ???

    def fetchAllUrlsPar(
      urls: List[String]
    ): ZIO[Any, Nothing, (List[(URL, Throwable)], List[(URL, String)])] =

      ZIO
        .foreachPar(urls) { urlString =>
          ZIO
            .attempt(URI.create(urlString).toURL)
            .flatMap { url =>
              fetchUrl(url).either.map {
                case Left(fetchError) => Left((url, fetchError))
                case Right(content)   => Right((url, content))
              }
            }
            .catchAll { parseError =>
              val dummyUrl = URI.create(s"http://invalid/$urlString").toURL
              ZIO.succeed(Left((dummyUrl, parseError)))
            }
        }
        .map(_.partitionMap(identity))
  }

}
