package zionomicon.exercises

package BestPractices {

  /**
   *   1. When should you use an _abstract class_ instead of a _sealed trait_
   *      to model domain errors? Provide an example using `UserServiceError`.
   */
  package AbstractClassVsSealedTrait {}

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
