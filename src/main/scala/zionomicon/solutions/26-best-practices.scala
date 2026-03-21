package zionomicon.solutions

package BestPractices {

  /**
   *   1. When should you use an _abstract class_ instead of a _sealed trait_ to
   *      model domain errors? Provide an example using `UserServiceError`.
   *
   * Answer:
   *
   * Use a sealed abstract class instead of a sealed trait when:
   *
   * a) You need Java interoperability — abstract classes can be caught directly
   * in Java catch blocks, whereas traits compiled to interfaces cannot be
   * caught without matching on the interface type.
   *
   * b) You need constructor parameters shared across all subtypes — an abstract
   * class lets you define common fields once in the constructor, avoiding
   * repetition in every case class.
   *
   * c) You want to extend Throwable/Exception — extending Exception (which is a
   * class) from a trait requires careful linearization; using a sealed abstract
   * class that extends Exception is more straightforward and idiomatic.
   *
   * Example below: UserServiceError extends Exception so that it can be used in
   * contexts that expect Throwable (e.g. legacy Java APIs), and carries a
   * shared `message` constructor parameter.
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
   *   2. In Scala 3, how would you model `UserServiceError` using enums instead
   *      of a sealed trait?
   *
   * Answer:
   *
   * In Scala 3, enums provide a concise way to define ADTs. They combine the
   * sealed trait + case class pattern into a single construct.
   *
   * Note: This code is Scala 3 only and will not compile under Scala 2.13. It
   * is provided here as a comment for reference.
   *
   * {{{
   * enum UserServiceError(val message: String) extends Exception(message):
   *   case UserAlreadyExists(username: String)
   *     extends UserServiceError(s"User '$username' already exists")
   *   case InvalidUsernameFormat(username: String)
   *     extends UserServiceError(s"Invalid username format: '$username'")
   *   case RegistrationQuotaExceeded(limit: Int)
   *     extends UserServiceError(s"Registration quota exceeded: limit is $limit")
   * }}}
   *
   * Benefits of enums over sealed traits:
   *
   * a) Less boilerplate — no need for separate case class declarations or
   * `extends Product with Serializable`.
   *
   * b) Built-in `values` and `valueOf` methods for simple enums (where all
   * cases are non-parameterized singletons), useful for serialization. Note:
   * These methods are not available for enums with parameterized cases.
   *
   * c) Exhaustivity checking works the same as sealed traits.
   *
   * d) Enums can have constructor parameters and extend classes (like
   * Exception), just like sealed abstract classes in Scala 2.
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
   * How would you model and handle errors in the `register` method? The goal is
   * to collect all errors and provide comprehensive feedback to the user
   * without failing fast.
   *
   * **Hint:** Consider using the `ZIO#validate` operator to validate input and
   * collect all errors.
   */
  package RegistrationValidation {

    import zio._

    sealed trait RegistrationError           extends Product with Serializable
    case class UsernameTooShort(length: Int) extends RegistrationError
    case class PasswordTooShort(length: Int) extends RegistrationError
    case class InvalidEmail(email: String)   extends RegistrationError
    case class AgeTooYoung(age: Int)         extends RegistrationError

    case class RegistrationForm(
      username: String,
      password: String,
      email: String,
      age: Int
    )

    object RegistrationService {

      def validateUsername(
        username: String
      ): IO[UsernameTooShort, String] =
        if (username.length >= 5) ZIO.succeed(username)
        else ZIO.fail(UsernameTooShort(username.length))

      def validatePassword(
        password: String
      ): IO[PasswordTooShort, String] =
        if (password.length >= 8) ZIO.succeed(password)
        else ZIO.fail(PasswordTooShort(password.length))

      def validateEmail(
        email: String
      ): IO[InvalidEmail, String] = {
        val parts = email.split("@")
        if (parts.length == 2 && parts(1).contains("."))
          ZIO.succeed(email)
        else ZIO.fail(InvalidEmail(email))
      }

      def validateAge(age: Int): IO[AgeTooYoung, Int] =
        if (age >= 18) ZIO.succeed(age)
        else ZIO.fail(AgeTooYoung(age))

      def register(
        username: String,
        password: String,
        email: String,
        age: Int
      ): IO[::[RegistrationError], RegistrationForm] = {
        val validations: List[IO[RegistrationError, Unit]] =
          List(
            validateUsername(username).unit,
            validatePassword(password).unit,
            validateEmail(email).unit,
            validateAge(age).unit
          )
        ZIO
          .validate(validations)(identity)
          .as(RegistrationForm(username, password, email, age))
      }
    }

    // --- Example Showcase ---

    object Exercise3Example extends ZIOAppDefault {

      def formatError(error: RegistrationError): String =
        error match {
          case UsernameTooShort(len) =>
            s"Username too short ($len chars, need at least 5)"
          case PasswordTooShort(len) =>
            s"Password too short ($len chars, need at least 8)"
          case InvalidEmail(email) =>
            s"Invalid email: '$email' (must contain '@' and a domain)"
          case AgeTooYoung(age) =>
            s"Must be 18 or older (got $age)"
        }

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 3: ZIO.validate ===")
        // Case 1: All fields invalid — all errors collected
        _ <- Console.printLine("\n--- Invalid input (all fields wrong) ---")
        _ <- RegistrationService
          .register("ab", "short", "bad-email", 15)
          .foldZIO(
            errors =>
              ZIO.foreach(errors.toList)(e =>
                Console.printLine(s"  Error: ${formatError(e)}")
              ),
            form =>
              Console.printLine(s"  Registered: $form")
          )
        // Case 2: Valid input — success
        _ <- Console.printLine("\n--- Valid input ---")
        _ <- RegistrationService
          .register("alice", "securepass", "alice@example.com", 25)
          .foldZIO(
            errors =>
              ZIO.foreach(errors.toList)(e =>
                Console.printLine(s"  Error: ${formatError(e)}")
              ),
            form =>
              Console.printLine(s"  Registered: $form")
          )
        // Case 3: Partial errors — only some fields invalid
        _ <- Console.printLine("\n--- Partial errors (username + age) ---")
        _ <- RegistrationService
          .register("ab", "securepass", "bob@test.com", 16)
          .foldZIO(
            errors =>
              ZIO.foreach(errors.toList)(e =>
                Console.printLine(s"  Error: ${formatError(e)}")
              ),
            form =>
              Console.printLine(s"  Registered: $form")
          )
      } yield ()
    }
  }

  /**
   *   4. Utilize ZIO Prelude's `Validation` data type to accumulate errors from
   *      the previous exercise. Compare this approach with the previous method
   *      and discuss the pros and cons of each.
   *
   * Comparison:
   *
   * ZIO#validate (Exercise 3): + Works directly with ZIO effects, so validation
   * can be effectful (e.g., check uniqueness against a database). + Familiar
   * API if you already use ZIO operators.
   *   - Requires `.unit` to unify heterogeneous success types in a collection,
   *     losing typed results.
   *   - Error type is `::[E]` (non-empty list), less ergonomic to pattern match
   *     on.
   *
   * ZIO Prelude Validation (Exercise 4): + Pure data type — no effects needed
   * for pure validation logic. + Preserves typed success values via `zipWith` /
   * `<*>`. + `NonEmptyChunk[E]` error accumulation is explicit and clean.
   *   - Cannot perform effectful validations (e.g., DB lookups) without
   *     converting to ZIO first.
   *   - Adds ZIO Prelude as a dependency.
   */
  package PreludeValidation {

    import zio.prelude._

    sealed trait RegistrationError           extends Product with Serializable
    case class UsernameTooShort(length: Int) extends RegistrationError
    case class PasswordTooShort(length: Int) extends RegistrationError
    case class InvalidEmail(email: String)   extends RegistrationError
    case class AgeTooYoung(age: Int)         extends RegistrationError

    case class RegistrationForm(
      username: String,
      password: String,
      email: String,
      age: Int
    )

    object RegistrationService {

      def validateUsername(
        username: String
      ): Validation[RegistrationError, String] =
        if (username.length >= 5)
          Validation.succeed(username)
        else
          Validation.fail(UsernameTooShort(username.length))

      def validatePassword(
        password: String
      ): Validation[RegistrationError, String] =
        if (password.length >= 8)
          Validation.succeed(password)
        else
          Validation.fail(PasswordTooShort(password.length))

      def validateEmail(
        email: String
      ): Validation[RegistrationError, String] = {
        val parts = email.split("@")
        if (parts.length == 2 && parts(1).contains("."))
          Validation.succeed(email)
        else
          Validation.fail(InvalidEmail(email))
      }

      def validateAge(
        age: Int
      ): Validation[RegistrationError, Int] =
        if (age >= 18) Validation.succeed(age)
        else Validation.fail(AgeTooYoung(age))

      def register(
        username: String,
        password: String,
        email: String,
        age: Int
      ): Validation[RegistrationError, RegistrationForm] =
        Validation.validateWith(
          validateUsername(username),
          validatePassword(password),
          validateEmail(email),
          validateAge(age)
        )(RegistrationForm)
    }

    // --- Example Showcase ---

    object Exercise4Example extends zio.ZIOAppDefault {
      import zio._

      def formatError(error: RegistrationError): String =
        error match {
          case UsernameTooShort(len) =>
            s"Username too short ($len chars, need at least 5)"
          case PasswordTooShort(len) =>
            s"Password too short ($len chars, need at least 8)"
          case InvalidEmail(email) =>
            s"Invalid email: '$email' (must contain '@' and a domain)"
          case AgeTooYoung(age) =>
            s"Must be 18 or older (got $age)"
        }

      def showResult(
        label: String,
        result: Validation[RegistrationError, RegistrationForm]
      ): ZIO[Any, Any, Unit] =
        result match {
          case Validation.Success(_, form) =>
            Console.printLine(s"$label\n  Registered: $form")
          case Validation.Failure(_, errors) =>
            Console.printLine(label) *>
              ZIO.foreach(errors.toList)(e =>
                Console.printLine(s"  Error: ${formatError(e)}")
              ) *> ZIO.unit
        }

      def run: ZIO[Any, Any, Unit] = for {
        _ <- Console.printLine("=== Exercise 4: ZIO Prelude Validation ===")
        // Case 1: All fields invalid
        _ <- showResult(
          "\n--- Invalid input (all fields wrong) ---",
          RegistrationService.register("ab", "short", "bad-email", 15)
        )
        // Case 2: Valid input
        _ <- showResult(
          "\n--- Valid input ---",
          RegistrationService
            .register("alice", "securepass", "alice@example.com", 25)
        )
        // Case 3: Partial errors
        _ <- showResult(
          "\n--- Partial errors (username + age) ---",
          RegistrationService.register("ab", "securepass", "bob@test.com", 16)
        )
      } yield ()
    }
  }

}
