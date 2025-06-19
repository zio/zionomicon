package zionomicon.solutions

import zio._

import java.net.URL

object ConcurrencyOperators {

  def foreachPar[R, E, A, B](
    in: Iterable[A]
  )(f: A => ZIO[R, E, B]): ZIO[R, E, List[B]] =
    ZIO.foreachPar(in.toList)(f)

  /*
    1. Implement the collectAllPar combinator using foreachPar.
   */
  def collectAllPar[R, E, A](
    in: Iterable[ZIO[R, E, A]]
  ): ZIO[R, E, List[A]] =
    foreachPar(in.toList)(identity)

  /*
    2. Write a function that takes a collection of ZIO effects and collects all the successful
      and failed results as a tuple.
   */
  def collectAllParResults[R, E, A](
    in: Iterable[ZIO[R, E, A]]
  ): ZIO[R, Nothing, (List[A], List[E])] =
    ZIO.partitionPar(in.toList)(identity).map { case (errors, successes) =>
      (successes.toList, errors.toList)
    }

  /*
    3. Assume you have given the following fetchUrl function that fetches a URL and
      returns a ZIO effect:

      And you have a list of URLs you want to fetch in parallel. Implement a function that
      fetches all the URLs in parallel and collects both the successful and failed results.
      Both successful and failed results should be paired with the URL they correspond
      to.
   */
  def fetchUrl(url: URL): ZIO[Any, Throwable, String] = ???

  def fetchAllUrlsPar(
    urls: List[String]
  ): ZIO[Any, Nothing, (List[(URL, Throwable)], List[(URL, String)])] =
    for {
      validUrls <- foreachPar(urls)(url =>
                     ZIO
                       .attempt(new URL(url))
                       .fold(
                         _ => Left(url),
                         url => Right(url)
                       )
                   )
      data <- ZIO.foreachPar(validUrls) { url =>
                url match {
                  case Left(invalid) =>
                    ZIO.succeed(
                      Left(new URL(invalid) -> new Throwable("Invalid URL"))
                    )
                  case Right(url) =>
                    fetchUrl(url).fold(
                      err => Left(url -> err),
                      data => Right(url -> data)
                    )
                }

              }
    } yield data.partitionMap(identity)

}
