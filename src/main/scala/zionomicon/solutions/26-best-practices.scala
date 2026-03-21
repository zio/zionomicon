package zionomicon.solutions

package BestPractices {

  /**
   *   1. When should you use an _abstract class_ instead of a _sealed trait_
   *      to model domain errors? Provide an example using `UserServiceError`.
   *
   * Answer:
   *
   * Use a sealed abstract class instead of a sealed trait when:
   *
   *   a) You need Java interoperability — abstract classes can be caught
   *      directly in Java catch blocks, whereas traits compiled to interfaces
   *      cannot be caught without matching on the interface type.
   *
   *   b) You need constructor parameters shared across all subtypes — an
   *      abstract class lets you define common fields once in the constructor,
   *      avoiding repetition in every case class.
   *
   *   c) You want to extend Throwable/Exception — extending Exception
   *      (which is a class) from a trait requires careful linearization;
   *      using a sealed abstract class that extends Exception is more
   *      straightforward and idiomatic.
   *
   * Example below: UserServiceError extends Exception so that it can be
   * used in contexts that expect Throwable (e.g. legacy Java APIs),
   * and carries a shared `message` constructor parameter.
   */
  package AbstractClassVsSealedTrait {

    sealed abstract class UserServiceError(val message: String)
        extends Exception(message)

    case class UserAlreadyExists(username: String)
        extends UserServiceError(s"User '$username' already exists")

    case class InvalidUsernameFormat(username: String)
        extends UserServiceError(
          s"Invalid username format: '$username'"
        )

    case class RegistrationQuotaExceeded(limit: Int)
        extends UserServiceError(
          s"Registration quota exceeded: limit is $limit"
        )
  }

  /**
   *   2. In Scala 3, how would you model `UserServiceError` using enums
   *      instead of a sealed trait?
   */
  package Scala3EnumErrors {}

  /**
   *   3. You are developing a user registration form with the following
   *      criteria:
   *
   *   - Username must be at least five characters long.
   *   - Password must be at least eight characters long.
   *   - Email must contain an "@" and a domain name.
   *   - Age must be 18 or older.
   *
   * How would you model and handle errors in the `register` method? The goal
   * is to collect all errors and provide comprehensive feedback to the user
   * without failing fast.
   *
   * **Hint:** Consider using the `ZIO#validate` operator to validate input
   * and collect all errors.
   */
  package RegistrationValidation {}

  /**
   *   4. Utilize ZIO Prelude's `Validation` data type to accumulate errors
   *      from the previous exercise. Compare this approach with the previous
   *      method and discuss the pros and cons of each.
   */
  package PreludeValidation {}

}
