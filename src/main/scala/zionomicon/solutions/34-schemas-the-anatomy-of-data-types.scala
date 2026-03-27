package zionomicon.solutions

package SchemaTheAnatomyOfDataTypes {

  /**
   *   1. Extend the query DSL with additional comparison operators.
   */
  package QueryDslExtendedOperators {

    import zio._
    import zio.test._

    // ── Core data types ──────────────────────────────────────────────────────

    /**
     * A typed accessor for a field `A` of a record `S`.
     *
     * Field identity is (recordType, fieldName). The `get` function extracts
     * the value from a concrete record; it is intentionally excluded from
     * equality so that two accessors for the same field are always equal
     * regardless of how the getter was constructed.
     *
     * Declared as a plain `class` (not `case class`) to avoid the misleading
     * generated `copy`/`unapply` methods that would expose `get` as a
     * structural field, contradicting our custom equality semantics.
     */
    final class FieldAccessor[S, A](
      val recordType: String,
      val fieldName: String,
      val get: S => A
    ) { self =>

      def ===(value: A): Query[S] = Query.Equal(self, value)

      def !==(value: A): Query[S] = Query.NotEqual(self, value)

      def >(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.GreaterThan(self, value, ord)

      def <(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.LessThan(self, value, ord)

      def >=(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.GreaterThanOrEqual(self, value, ord)

      def <=(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.LessThanOrEqual(self, value, ord)

      override def equals(obj: Any): Boolean = obj match {
        case other: FieldAccessor[_, _] =>
          recordType == other.recordType && fieldName == other.fieldName
        case _ => false
      }

      override def hashCode: Int = (recordType, fieldName).hashCode

      override def toString: String = s"FieldAccessor($recordType.$fieldName)"
    }

    object FieldAccessor {

      def apply[S, A](
        recordType: String,
        fieldName: String,
        get: S => A
      ): FieldAccessor[S, A] =
        new FieldAccessor(recordType, fieldName, get)

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

      // Ordering is captured at operator call-site so the interpreter
      // never needs to know the concrete type A.
      case class GreaterThan[S, A](
        field: FieldAccessor[S, A],
        value: A,
        ord: Ordering[A]
      ) extends Query[S]

      case class LessThan[S, A](
        field: FieldAccessor[S, A],
        value: A,
        ord: Ordering[A]
      ) extends Query[S]

      case class GreaterThanOrEqual[S, A](
        field: FieldAccessor[S, A],
        value: A,
        ord: Ordering[A]
      ) extends Query[S]

      case class LessThanOrEqual[S, A](
        field: FieldAccessor[S, A],
        value: A,
        ord: Ordering[A]
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
        FieldAccessor[Person, String]("Person", "name", _.name)

      val age: FieldAccessor[Person, Int] =
        FieldAccessor[Person, Int]("Person", "age", _.age)
    }

    // ── Query interpreter ────────────────────────────────────────────────────

    /**
     * Evaluates a query against a record. The interpreter is fully generic:
     * it knows nothing about `Person` or any other domain type. Field values
     * are extracted via `FieldAccessor.get`; ordering is provided by the
     * `Ordering[A]` stored in each comparison node.
     */
    object QueryInterpreter {

      def evaluate[S](query: Query[S], record: S): Boolean =
        query match {
          case Query.Equal(field, expected) =>
            field.get(record) == expected

          case Query.NotEqual(field, expected) =>
            field.get(record) != expected

          case Query.GreaterThan(field, threshold, ord) =>
            ord.compare(field.get(record), threshold) > 0

          case Query.LessThan(field, threshold, ord) =>
            ord.compare(field.get(record), threshold) < 0

          case Query.GreaterThanOrEqual(field, threshold, ord) =>
            ord.compare(field.get(record), threshold) >= 0

          case Query.LessThanOrEqual(field, threshold, ord) =>
            ord.compare(field.get(record), threshold) <= 0

          case Query.Contains(field, substring) =>
            field.get(record).contains(substring)

          case Query.And(left, right) =>
            evaluate(left, record) && evaluate(right, record)

          case Query.Or(left, right) =>
            evaluate(left, record) || evaluate(right, record)

          case Query.Not(inner) =>
            !evaluate(inner, record)
        }
    }

    // ── Spec ─────────────────────────────────────────────────────────────────

    object QueryDslSpec extends ZIOSpecDefault {

      val john: Person  = Person("John Doe", 30)
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
              assertTrue(q == Query.GreaterThan(Person.age, 18, Ordering[Int]))
            },
            test("< produces LessThan") {
              val q: Query[Person] = Person.age < 65
              assertTrue(q == Query.LessThan(Person.age, 65, Ordering[Int]))
            },
            test(">= produces GreaterThanOrEqual") {
              val q: Query[Person] = Person.age >= 18
              assertTrue(q == Query.GreaterThanOrEqual(Person.age, 18, Ordering[Int]))
            },
            test("<= produces LessThanOrEqual") {
              val q: Query[Person] = Person.age <= 65
              assertTrue(q == Query.LessThanOrEqual(Person.age, 65, Ordering[Int]))
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
                  Query.GreaterThan(Person.age, 18, Ordering[Int])
                )
              )
            },
            test("|| produces Or") {
              val q = (Person.name === "John") || (Person.age > 50)
              assertTrue(
                q == Query.Or(
                  Query.Equal(Person.name, "John"),
                  Query.GreaterThan(Person.age, 50, Ordering[Int])
                )
              )
            },
            test("unary_! produces Not") {
              val q = !(Person.age > 18)
              assertTrue(
                q == Query.Not(Query.GreaterThan(Person.age, 18, Ordering[Int]))
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
            test("! negates condition") {
              assertTrue(
                // john.age=30: !(30>50) = !false = true
                QueryInterpreter.evaluate(!(Person.age > 50), john),
                // alice.age=42: !(42>40) = !true = false  →  !false = true
                !QueryInterpreter.evaluate(!(Person.age > 40), alice)
              )
            },
            test("contains is case-sensitive") {
              assertTrue(
                QueryInterpreter.evaluate(Person.name.contains("John"), john),
                !QueryInterpreter.evaluate(Person.name.contains("john"), john)
              )
            },
          ),
          suite("FieldAccessor equality semantics")(
            test("same (recordType, fieldName) are equal regardless of getter") {
              val a1 = FieldAccessor[Person, String]("Person", "name", _.name)
              val a2 = FieldAccessor[Person, String]("Person", "name", p => p.name)
              assertTrue(a1 == a2, a1.hashCode == a2.hashCode)
            },
          ),
          suite("interpreter works on a second domain type")(
            test("evaluates queries over Product records") {
              case class Product(sku: String, price: Double)
              val sku   = FieldAccessor[Product, String]("Product", "sku", _.sku)
              val price = FieldAccessor[Product, Double]("Product", "price", _.price)

              val products = List(
                Product("A001", 9.99),
                Product("B002", 49.99),
                Product("A003", 19.99)
              )

              val query   = sku.contains("A") && (price > 15.0)
              val results = products.filter(p => QueryInterpreter.evaluate(query, p))

              assertTrue(results == List(Product("A003", 19.99)))
            }
          )
        )
    }

    // ── Example Showcase ─────────────────────────────────────────────────────

    object Exercise1Example extends ZIOAppDefault {

      val people: List[Person] = List(
        Person("Alice", 28),
        Person("Bob", 17),
        Person("John Doe", 35),
        Person("Carol", 42),
        Person("Dave", 35)
      )

      def find(query: Query[Person]): List[Person] =
        people.filter(p => QueryInterpreter.evaluate(query, p))

      def run: ZIO[Any, Throwable, Unit] =
        for {
          _ <- Console.printLine("=== Query DSL Example ===")
          _ <- Console.printLine("\nAll adults (age >= 18):")
          _ <- ZIO.foreach(find(Person.age >= 18))(p => Console.printLine(s"  $p"))
          _ <- Console.printLine("\nPeople in their 30s (age >= 30 && age <= 39):")
          _ <- ZIO.foreach(find((Person.age >= 30) && (Person.age <= 39)))(p =>
                 Console.printLine(s"  $p")
               )
          _ <- Console.printLine("\nPeople named 'John' or older than 40:")
          _ <- ZIO.foreach(find(Person.name.contains("John") || (Person.age > 40)))(p =>
                 Console.printLine(s"  $p")
               )
          _ <- Console.printLine("\nMinors (NOT age >= 18):")
          _ <- ZIO.foreach(find(!(Person.age >= 18)))(p => Console.printLine(s"  $p"))
        } yield ()
    }
  }

  /**
   *   2. Implement nested query support in the Query DSL.
   */
  /**
   *   2. Implement nested query support in the Query DSL.
   */
  package NestedQuerySupport {

    import zio._
    import zio.test._

    // ── Core data types (reused from exercise 1 with / operator added) ────────

    final class FieldAccessor[S, A](
      val recordType: String,
      val fieldName: String,
      val get: S => A
    ) { self =>

      /** Compose two accessors into a single path: `outer / inner`. */
      def /[B](inner: FieldAccessor[A, B]): FieldAccessor[S, B] =
        FieldAccessor(
          recordType,
          s"$fieldName.${inner.fieldName}",
          s => inner.get(self.get(s))
        )

      def ===(value: A): Query[S] = Query.Equal(self, value)

      def !==(value: A): Query[S] = Query.NotEqual(self, value)

      def >(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.GreaterThan(self, value, ord)

      def <(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.LessThan(self, value, ord)

      def >=(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.GreaterThanOrEqual(self, value, ord)

      def <=(value: A)(implicit ord: Ordering[A]): Query[S] =
        Query.LessThanOrEqual(self, value, ord)

      override def equals(obj: Any): Boolean = obj match {
        case other: FieldAccessor[_, _] =>
          recordType == other.recordType && fieldName == other.fieldName
        case _ => false
      }

      override def hashCode: Int = (recordType, fieldName).hashCode

      override def toString: String = s"FieldAccessor($recordType.$fieldName)"
    }

    object FieldAccessor {

      def apply[S, A](
        recordType: String,
        fieldName: String,
        get: S => A
      ): FieldAccessor[S, A] =
        new FieldAccessor(recordType, fieldName, get)

      implicit class StringOps[S](val self: FieldAccessor[S, String])
          extends AnyVal {

        def contains(value: String): Query[S] =
          Query.Contains(self, value)
      }
    }

    sealed trait Query[S] { self =>
      def &&(that: Query[S]): Query[S] = Query.And(self, that)
      def ||(that: Query[S]): Query[S] = Query.Or(self, that)
      def unary_! : Query[S]           = Query.Not(self)
    }

    object Query {
      case class Equal[S, A](field: FieldAccessor[S, A], value: A)       extends Query[S]
      case class NotEqual[S, A](field: FieldAccessor[S, A], value: A)    extends Query[S]
      case class GreaterThan[S, A](field: FieldAccessor[S, A], value: A, ord: Ordering[A]) extends Query[S]
      case class LessThan[S, A](field: FieldAccessor[S, A], value: A, ord: Ordering[A])    extends Query[S]
      case class GreaterThanOrEqual[S, A](field: FieldAccessor[S, A], value: A, ord: Ordering[A]) extends Query[S]
      case class LessThanOrEqual[S, A](field: FieldAccessor[S, A], value: A, ord: Ordering[A])    extends Query[S]
      case class Contains[S](field: FieldAccessor[S, String], value: String) extends Query[S]
      case class And[S](left: Query[S], right: Query[S]) extends Query[S]
      case class Or[S](left: Query[S], right: Query[S])  extends Query[S]
      case class Not[S](query: Query[S])                 extends Query[S]
    }

    object QueryInterpreter {
      def evaluate[S](query: Query[S], record: S): Boolean = query match {
        case Query.Equal(field, expected)                    => field.get(record) == expected
        case Query.NotEqual(field, expected)                 => field.get(record) != expected
        case Query.GreaterThan(field, threshold, ord)        => ord.compare(field.get(record), threshold) > 0
        case Query.LessThan(field, threshold, ord)           => ord.compare(field.get(record), threshold) < 0
        case Query.GreaterThanOrEqual(field, threshold, ord) => ord.compare(field.get(record), threshold) >= 0
        case Query.LessThanOrEqual(field, threshold, ord)    => ord.compare(field.get(record), threshold) <= 0
        case Query.Contains(field, substring)                => field.get(record).contains(substring)
        case Query.And(left, right)                          => evaluate(left, record) && evaluate(right, record)
        case Query.Or(left, right)                           => evaluate(left, record) || evaluate(right, record)
        case Query.Not(inner)                                => !evaluate(inner, record)
      }
    }

    // ── Domain model ──────────────────────────────────────────────────────────

    case class Address(country: String, city: String, street: String)

    object Address {
      val country: FieldAccessor[Address, String] =
        FieldAccessor("Address", "country", _.country)
      val city: FieldAccessor[Address, String] =
        FieldAccessor("Address", "city", _.city)
      val street: FieldAccessor[Address, String] =
        FieldAccessor("Address", "street", _.street)
    }

    case class Person(name: String, age: Int, address: Address)

    object Person {
      val name: FieldAccessor[Person, String] =
        FieldAccessor("Person", "name", _.name)
      val age: FieldAccessor[Person, Int] =
        FieldAccessor("Person", "age", _.age)
      val address: FieldAccessor[Person, Address] =
        FieldAccessor("Person", "address", _.address)
    }

    // ── Spec ──────────────────────────────────────────────────────────────────

    object NestedQuerySpec extends ZIOSpecDefault {

      val tokyo: Address   = Address("Japan", "Tokyo", "Shibuya")
      val london: Address  = Address("UK", "London", "Baker St")
      val john: Person     = Person("John Doe", 35, tokyo)
      val alice: Person    = Person("Alice", 28, london)
      val charlie: Person  = Person("Charlie", 42, tokyo)

      override def spec: Spec[TestEnvironment with Scope, Any] =
        suite("Nested Query DSL")(
          suite("/ operator composes accessors")(
            test("/ produces a path accessor with combined fieldName") {
              val cityAccessor = Person.address / Address.city
              assertTrue(
                cityAccessor.recordType == "Person",
                cityAccessor.fieldName  == "address.city"
              )
            },
            test("/ chains getter functions correctly") {
              val cityAccessor = Person.address / Address.city
              assertTrue(cityAccessor.get(john) == "Tokyo")
            },
            test("deeply nested path composes transitively") {
              case class World(person: Person)
              val worldPerson = FieldAccessor[World, Person]("World", "person", _.person)
              val cityInWorld = worldPerson / Person.address / Address.city
              assertTrue(
                cityInWorld.fieldName == "person.address.city",
                cityInWorld.get(World(john)) == "Tokyo"
              )
            }
          ),
          suite("nested query evaluation")(
            test("=== on nested field matches correctly") {
              val q = Person.address / Address.city === "Tokyo"
              assertTrue(
                QueryInterpreter.evaluate(q, john),
                !QueryInterpreter.evaluate(q, alice)
              )
            },
            test("combines nested and top-level conditions with &&") {
              val q = (Person.name === "John Doe") && (Person.address / Address.city === "Tokyo")
              assertTrue(
                QueryInterpreter.evaluate(q, john),
                !QueryInterpreter.evaluate(q, alice),
                !QueryInterpreter.evaluate(q, charlie)
              )
            },
            test("combines nested conditions with ||") {
              val q = (Person.address / Address.city === "Tokyo") ||
                      (Person.address / Address.city === "London")
              assertTrue(
                QueryInterpreter.evaluate(q, john),
                QueryInterpreter.evaluate(q, alice),
                !QueryInterpreter.evaluate(q, Person("Bob", 20, Address("US", "NYC", "5th Ave")))
              )
            },
            test("contains on nested string field") {
              val q = (Person.address / Address.city).contains("ok")
              assertTrue(
                QueryInterpreter.evaluate(q, john),   // "Tokyo" contains "ok"
                !QueryInterpreter.evaluate(q, alice)  // "London" does not
              )
            },
            test("> on nested numeric field") {
              case class Order(id: String, item: OrderItem)
              case class OrderItem(name: String, price: Double)
              val itemPrice = FieldAccessor[Order, Double](
                "Order", "item.price", o => o.item.price
              )
              val q = itemPrice > 20.0
              assertTrue(
                QueryInterpreter.evaluate(q, Order("1", OrderItem("Widget", 49.99))),
                !QueryInterpreter.evaluate(q, Order("2", OrderItem("Bolt", 0.99)))
              )
            }
          ),
          suite("/ accessor equality")(
            test("same path accessors are equal") {
              val p1 = Person.address / Address.city
              val p2 = Person.address / Address.city
              assertTrue(p1 == p2)
            },
            test("different path accessors are not equal") {
              assertTrue((Person.address / Address.city) != (Person.address / Address.street))
            }
          )
        )
    }

    // ── Example Showcase ──────────────────────────────────────────────────────

    object Exercise2Example extends ZIOAppDefault {

      val people: List[Person] = List(
        Person("John Doe", 35, Address("Japan", "Tokyo", "Shibuya")),
        Person("Alice",    28, Address("UK",    "London", "Baker St")),
        Person("Bob",      42, Address("Japan", "Tokyo", "Harajuku")),
        Person("Carol",    31, Address("US",    "NYC",   "5th Ave"))
      )

      def find(query: Query[Person]): List[Person] =
        people.filter(p => QueryInterpreter.evaluate(query, p))

      def run: ZIO[Any, Throwable, Unit] =
        for {
          _ <- Console.printLine("=== Nested Query DSL Example ===")
          _ <- Console.printLine("\nPeople in Tokyo:")
          _ <- ZIO.foreach(find(Person.address / Address.city === "Tokyo"))(p =>
                 Console.printLine(s"  ${p.name}")
               )
          _ <- Console.printLine("\nJohn Doe in Tokyo:")
          _ <- ZIO.foreach(
                 find((Person.name === "John Doe") && (Person.address / Address.city === "Tokyo"))
               )(p => Console.printLine(s"  ${p.name}"))
          _ <- Console.printLine("\nPeople in Japan OR older than 35:")
          _ <- ZIO.foreach(
                 find((Person.address / Address.country === "Japan") || (Person.age > 35))
               )(p => Console.printLine(s"  ${p.name} (${p.age}, ${p.address.city})"))
        } yield ()
    }
  }

  /**
   *   3. Write a CSV codec that supports encoding and decoding of record types.
   */
  package CsvCodec {}

}
