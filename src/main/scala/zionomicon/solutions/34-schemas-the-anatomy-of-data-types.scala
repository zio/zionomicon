package zionomicon.solutions

package SchemaTheAnatomyOfDataTypes {

  /**
   *   1. Extend the query DSL with additional comparison operators.
   */
  package QueryDslExtendedOperators {

    import zio._
    import zio.test._

    // ── Core data types ──────────────────────────────────────────────────────

    case class FieldAccessor[S, A](recordType: String, fieldName: String) {
      self =>

      def ===(value: A): Query[S] = Query.Equal(self, value)

      def !==(value: A): Query[S] = Query.NotEqual(self, value)

      def >(value: A): Query[S] = Query.GreaterThan(self, value)

      def <(value: A): Query[S] = Query.LessThan(self, value)

      def >=(value: A): Query[S] = Query.GreaterThanOrEqual(self, value)

      def <=(value: A): Query[S] = Query.LessThanOrEqual(self, value)
    }

    object FieldAccessor {

      implicit class StringOps[S](val self: FieldAccessor[S, String])
          extends AnyVal {

        def contains(value: String): Query[S] =
          Query.Contains(self, value)
      }
    }

    sealed trait Query[S] {
      self =>

      def &&(that: Query[S]): Query[S] = Query.And(self, that)

      def ||(that: Query[S]): Query[S] = Query.Or(self, that)

      def unary_! : Query[S] = Query.Not(self)
    }

    object Query {
      case class Equal[S, A](field: FieldAccessor[S, A], value: A)
          extends Query[S]

      case class NotEqual[S, A](field: FieldAccessor[S, A], value: A)
          extends Query[S]

      case class GreaterThan[S, A](field: FieldAccessor[S, A], value: A)
          extends Query[S]

      case class LessThan[S, A](field: FieldAccessor[S, A], value: A)
          extends Query[S]

      case class GreaterThanOrEqual[S, A](
        field: FieldAccessor[S, A],
        value: A
      ) extends Query[S]

      case class LessThanOrEqual[S, A](
        field: FieldAccessor[S, A],
        value: A
      ) extends Query[S]

      case class Contains[S](field: FieldAccessor[S, String], value: String)
          extends Query[S]

      case class And[S](left: Query[S], right: Query[S]) extends Query[S]

      case class Or[S](left: Query[S], right: Query[S]) extends Query[S]

      case class Not[S](query: Query[S]) extends Query[S]
    }

    // ── Domain model & accessors ─────────────────────────────────────────────

    case class Person(name: String, age: Int)

    object Person {
      val name: FieldAccessor[Person, String] =
        FieldAccessor[Person, String]("Person", "name")

      val age: FieldAccessor[Person, Int] =
        FieldAccessor[Person, Int]("Person", "age")
    }

    // ── Query interpreter ────────────────────────────────────────────────────

    object QueryInterpreter {

      def evaluate[S](query: Query[S], value: S): Boolean =
        query match {
          case Query.Equal(field, expected) =>
            field.fieldName match {
              case "name" =>
                value.asInstanceOf[Person].name == expected
              case "age" =>
                value.asInstanceOf[Person].age == expected
              case _ => false
            }

          case Query.NotEqual(field, expected) =>
            !evaluate(Query.Equal(field, expected), value)

          case Query.GreaterThan(field, threshold) =>
            (field.fieldName, threshold) match {
              case ("age", n: Int) => value.asInstanceOf[Person].age > n
              case _               => false
            }

          case Query.LessThan(field, threshold) =>
            (field.fieldName, threshold) match {
              case ("age", n: Int) => value.asInstanceOf[Person].age < n
              case _               => false
            }

          case Query.GreaterThanOrEqual(field, threshold) =>
            (field.fieldName, threshold) match {
              case ("age", n: Int) => value.asInstanceOf[Person].age >= n
              case _               => false
            }

          case Query.LessThanOrEqual(field, threshold) =>
            (field.fieldName, threshold) match {
              case ("age", n: Int) => value.asInstanceOf[Person].age <= n
              case _               => false
            }

          case Query.Contains(field, substring) =>
            field.fieldName match {
              case "name" =>
                value.asInstanceOf[Person].name.contains(substring)
              case _ => false
            }

          case Query.And(left, right) =>
            evaluate(left, value) && evaluate(right, value)

          case Query.Or(left, right) =>
            evaluate(left, value) || evaluate(right, value)

          case Query.Not(inner) =>
            !evaluate(inner, value)
        }
    }

    // ── Spec ─────────────────────────────────────────────────────────────────

    object QueryDslSpec extends ZIOSpecDefault {

      val john: Person = Person("John Doe", 30)
      val alice: Person = Person("Alice", 42)

      override def spec: Spec[TestEnvironment with Scope, Any] =
        suite("Extended Query DSL")(
          suite("structural encoding")(
            test("=== produces Equal") {
              assertTrue(
                Person.name === "John" ==
                  Query.Equal(Person.name, "John")
              )
            },
            test("!== produces NotEqual") {
              assertTrue(
                (Person.age !== 99) ==
                  Query.NotEqual(Person.age, 99)
              )
            },
            test("> produces GreaterThan") {
              val q: Query[Person] = Person.age > 18
              assertTrue(q == Query.GreaterThan(Person.age, 18))
            },
            test("< produces LessThan") {
              val q: Query[Person] = Person.age < 65
              assertTrue(q == Query.LessThan(Person.age, 65))
            },
            test(">= produces GreaterThanOrEqual") {
              val q: Query[Person] = Person.age >= 18
              assertTrue(q == Query.GreaterThanOrEqual(Person.age, 18))
            },
            test("<= produces LessThanOrEqual") {
              val q: Query[Person] = Person.age <= 65
              assertTrue(q == Query.LessThanOrEqual(Person.age, 65))
            },
            test("contains produces Contains") {
              assertTrue(
                Person.name.contains("John") ==
                  Query.Contains(Person.name, "John")
              )
            },
            test("&& produces And") {
              val q = (Person.name === "John") && (Person.age > 18)
              assertTrue(
                q == Query.And(
                  Query.Equal(Person.name, "John"),
                  Query.GreaterThan(Person.age, 18)
                )
              )
            },
            test("|| produces Or") {
              val q = (Person.name === "John") || (Person.age > 50)
              assertTrue(
                q == Query.Or(
                  Query.Equal(Person.name, "John"),
                  Query.GreaterThan(Person.age, 50)
                )
              )
            },
            test("unary_! produces Not") {
              val q = !(Person.age > 18)
              assertTrue(
                q == Query.Not(Query.GreaterThan(Person.age, 18))
              )
            }
          ),
          suite("query evaluation")(
            test("=== matches equal values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.name === "John Doe", john),
                !QueryInterpreter.evaluate(Person.name === "Alice", john)
              )
            },
            test("!== matches non-equal values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.age !== 99, john),
                !QueryInterpreter.evaluate(Person.age !== 30, john)
              )
            },
            test("> matches strictly greater values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.age > 18, john),
                !QueryInterpreter.evaluate(Person.age > 30, john)
              )
            },
            test("< matches strictly less values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.age < 65, john),
                !QueryInterpreter.evaluate(Person.age < 30, john)
              )
            },
            test(">= matches greater-than-or-equal values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.age >= 30, john),
                QueryInterpreter.evaluate(Person.age >= 29, john),
                !QueryInterpreter.evaluate(Person.age >= 31, john)
              )
            },
            test("<= matches less-than-or-equal values") {
              assertTrue(
                QueryInterpreter.evaluate(Person.age <= 30, john),
                QueryInterpreter.evaluate(Person.age <= 31, john),
                !QueryInterpreter.evaluate(Person.age <= 29, john)
              )
            },
            test("contains matches substrings") {
              assertTrue(
                QueryInterpreter.evaluate(Person.name.contains("John"), john),
                !QueryInterpreter
                  .evaluate(Person.name.contains("Alice"), john)
              )
            },
            test("&& requires both conditions") {
              val q = (Person.name === "John Doe") && (Person.age > 18)
              assertTrue(
                QueryInterpreter.evaluate(q, john),
                !QueryInterpreter.evaluate(q, alice)
              )
            },
            test("|| requires at least one condition") {
              val q = (Person.name === "John Doe") || (Person.age > 40)
              assertTrue(
                QueryInterpreter.evaluate(q, john),
                QueryInterpreter.evaluate(q, alice),
                !QueryInterpreter
                  .evaluate(q, Person("Bob", 25))
              )
            },
            test("! negates condition") {
              assertTrue(
                // john.age=30: !(30>50) = !false = true
                QueryInterpreter.evaluate(!(Person.age > 50), john),
                // alice.age=42: !(42>40) = !true = false  →  !false = true
                !QueryInterpreter.evaluate(!(Person.age > 40), alice)
              )
            }
          )
        )
    }
  }

  /**
   *   2. Implement nested query support in the Query DSL.
   */
  package NestedQuerySupport {}

  /**
   *   3. Write a CSV codec that supports encoding and decoding of record types.
   */
  package CsvCodec {}

}
