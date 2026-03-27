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
  package CsvCodec {

    import zio._
    import zio.test._

    // ── DynamicValue ──────────────────────────────────────────────────────────
    //
    // A structural representation of a value, independent of its static type.
    // Mirrors ZIO Schema's DynamicValue design:
    //  - Record  : an ordered list of named fields (preserves field order for CSV)
    //  - Primitive: a serialised string (sufficient for CSV round-trips)

    sealed trait DynamicValue

    object DynamicValue {
      final case class Record(fields: List[(String, DynamicValue)]) extends DynamicValue
      final case class Primitive(value: String)                     extends DynamicValue
    }

    // ── Schema[A] ─────────────────────────────────────────────────────────────
    //
    // A Schema[A] is a reified description of type A that can:
    //  - convert a live A into its DynamicValue (toDynamic)
    //  - reconstruct an A from a DynamicValue (fromDynamic)
    //  - enumerate field names (fieldNames — empty for primitives)
    //
    // Follows the same pattern as ZIO Schema without the library dependency.

    sealed trait Schema[A] {
      def toDynamic(value: A): DynamicValue
      def fromDynamic(dv: DynamicValue): Either[String, A]
      def fieldNames: List[String]
    }

    object Schema {

      // ── Primitive schemas ──────────────────────────────────────────────────

      private def mkPrimitive[A](
        serialize: A => String,
        deserialize: String => Either[String, A]
      ): Schema[A] = new Schema[A] {
        def toDynamic(value: A): DynamicValue = DynamicValue.Primitive(serialize(value))
        def fromDynamic(dv: DynamicValue): Either[String, A] = dv match {
          case DynamicValue.Primitive(s) => deserialize(s)
          case _                          => Left("Expected Primitive, got Record")
        }
        def fieldNames: List[String] = Nil
      }

      implicit val string: Schema[String] =
        mkPrimitive(identity, Right(_))

      implicit val int: Schema[Int] =
        mkPrimitive(_.toString, s => s.toIntOption.toRight(s"Not an Int: '$s'"))

      implicit val double: Schema[Double] =
        mkPrimitive(_.toString, s => s.toDoubleOption.toRight(s"Not a Double: '$s'"))

      implicit val boolean: Schema[Boolean] =
        mkPrimitive(
          _.toString,
          {
            case "true"  => Right(true)
            case "false" => Right(false)
            case s       => Left(s"Not a Boolean: '$s'")
          }
        )

      // ── Schema fields ──────────────────────────────────────────────────────
      //
      // SchemaField is an existential: it hides the concrete FieldType while
      // still giving the record schema everything it needs (name, dynamic
      // encode/decode). This is the same trick used inside ZIO Schema itself.

      sealed abstract class SchemaField[S] {
        type FieldType
        def name: String
        def fieldSchema: Schema[FieldType]
        def get: S => FieldType

        final def toDynamic(s: S): (String, DynamicValue) =
          name -> fieldSchema.toDynamic(get(s))

        // Returns Either[String, Any] so the record schema can collect all
        // field values in a List[Any] and pass them to construct().
        final def fromDynamic(dv: DynamicValue): Either[String, Any] =
          fieldSchema.fromDynamic(dv)
      }

      object SchemaField {
        def apply[S, A](
          fieldName: String,
          getter: S => A
        )(implicit s: Schema[A]): SchemaField[S] =
          new SchemaField[S] {
            type FieldType = A
            val name        = fieldName
            val fieldSchema = s
            val get         = getter
          }
      }

      // ── Record schema ──────────────────────────────────────────────────────
      //
      // Encodes A as a Record by applying each field's schema.
      // Decodes by looking up each field by name in the DynamicValue.Record,
      // collecting the results with foldRight (preserves field order without
      // a reverse), then delegating reconstruction to the caller-supplied
      // construct function.

      def record[A](
        schemaFields: List[SchemaField[A]]
      )(construct: List[Any] => Either[String, A]): Schema[A] =
        new Schema[A] {
          val fieldNames: List[String] = schemaFields.map(_.name)

          def toDynamic(value: A): DynamicValue =
            DynamicValue.Record(schemaFields.map(_.toDynamic(value)))

          def fromDynamic(dv: DynamicValue): Either[String, A] = dv match {
            case DynamicValue.Record(fieldValues) =>
              val byName = fieldValues.toMap
              schemaFields
                .foldRight[Either[String, List[Any]]](Right(Nil)) { (field, acc) =>
                  for {
                    rest <- acc
                    fv   <- byName.get(field.name).toRight(s"Missing field: '${field.name}'")
                    v    <- field.fromDynamic(fv)
                  } yield v :: rest
                }
                .flatMap(construct)
            case _ => Left("Expected Record, got Primitive")
          }
        }
    }

    // ── CsvEncoder[A] ─────────────────────────────────────────────────────────

    trait CsvEncoder[A] {
      def headers: List[String]
      def encodeRow(value: A): List[String]

      def encode(values: List[A]): String =
        (headers.mkString(",") :: values.map(v => encodeRow(v).mkString(","))).mkString("\n")
    }

    // ── CsvDecoder[A] ─────────────────────────────────────────────────────────

    trait CsvDecoder[A] {
      def decode(csv: String): Either[String, List[A]]
    }

    // ── CsvCodec factory ──────────────────────────────────────────────────────
    //
    // Derives encoder and decoder from Schema[A].
    //
    // Encoding path: A  →  schema.toDynamic  →  DynamicValue.Record
    //                →  extract Primitive strings in field order  →  CSV row
    //
    // Decoding path: CSV row  →  split by comma  →  DynamicValue.Record
    //                →  schema.fromDynamic  →  A

    object CsvCodec {

      private def traverseEither[A, B](
        list: List[A]
      )(f: A => Either[String, B]): Either[String, List[B]] =
        list.foldRight[Either[String, List[B]]](Right(Nil)) { (a, acc) =>
          for { rest <- acc; b <- f(a) } yield b :: rest
        }

      def encoder[A](schema: Schema[A]): Either[String, CsvEncoder[A]] =
        schema.fieldNames match {
          case Nil =>
            Left("CsvEncoder requires a record schema with at least one field")
          case hdrs =>
            Right(new CsvEncoder[A] {
              def headers: List[String] = hdrs

              def encodeRow(value: A): List[String] =
                schema.toDynamic(value) match {
                  case DynamicValue.Record(fields) =>
                    fields.map {
                      case (_, DynamicValue.Primitive(v)) => v
                      case (name, _) =>
                        sys.error(s"Nested records not supported in CSV: field '$name'")
                    }
                  case _ =>
                    sys.error("Schema.toDynamic returned Primitive for a record schema")
                }
            })
        }

      def decoder[A](schema: Schema[A]): Either[String, CsvDecoder[A]] =
        schema.fieldNames match {
          case Nil =>
            Left("CsvDecoder requires a record schema with at least one field")
          case _ =>
            Right(new CsvDecoder[A] {
              def decode(csv: String): Either[String, List[A]] = {
                val lines = csv.split("\n", -1).toList
                lines match {
                  case Nil => Right(Nil)
                  case headerLine :: dataLines =>
                    val parsedHeaders = headerLine.split(",", -1).toList
                    traverseEither(dataLines.filter(_.nonEmpty)) { line =>
                      val values = line.split(",", -1).toList
                      if (values.length != parsedHeaders.length)
                        Left(
                          s"Expected ${parsedHeaders.length} columns, got ${values.length}"
                        )
                      else {
                        val record = DynamicValue.Record(
                          parsedHeaders.zip(values).map { case (n, v) =>
                            n -> DynamicValue.Primitive(v)
                          }
                        )
                        schema.fromDynamic(record)
                      }
                    }
                }
              }
            })
        }

      def make[A](schema: Schema[A]): Either[String, (CsvEncoder[A], CsvDecoder[A])] =
        for {
          enc <- encoder(schema)
          dec <- decoder(schema)
        } yield (enc, dec)
    }

    // ── Domain model & schema for tests ──────────────────────────────────────

    case class Employee(name: String, age: Int, active: Boolean, salary: Double)

    object Employee {
      implicit val schema: Schema[Employee] =
        Schema.record[Employee](
          List(
            Schema.SchemaField("name",   _.name),
            Schema.SchemaField("age",    _.age),
            Schema.SchemaField("active", _.active),
            Schema.SchemaField("salary", _.salary)
          )
        ) {
          case List(name, age, active, salary) =>
            Right(
              Employee(
                name.asInstanceOf[String],
                age.asInstanceOf[Int],
                active.asInstanceOf[Boolean],
                salary.asInstanceOf[Double]
              )
            )
          case other => Left(s"Employee: expected 4 fields, got ${other.length}")
        }
    }

    // ── Spec ──────────────────────────────────────────────────────────────────

    object CsvCodecSpec extends ZIOSpecDefault {

      val alice: Employee   = Employee("Alice",   30, active = true,  salary = 75000.0)
      val bob: Employee     = Employee("Bob",     25, active = false, salary = 55000.0)
      val charlie: Employee = Employee("Charlie", 45, active = true,  salary = 120000.0)

      val enc: CsvEncoder[Employee] = CsvCodec.encoder(Employee.schema).toOption.get
      val dec: CsvDecoder[Employee] = CsvCodec.decoder(Employee.schema).toOption.get

      override def spec: Spec[TestEnvironment with Scope, Any] =
        suite("CSV Codec")(
          suite("CsvEncoder")(
            test("headers match schema field names") {
              assertTrue(enc.headers == List("name", "age", "active", "salary"))
            },
            test("encodeRow produces comma-separated primitive strings") {
              assertTrue(enc.encodeRow(alice) == List("Alice", "30", "true", "75000.0"))
            },
            test("encode produces header line followed by data rows") {
              val csv   = enc.encode(List(alice, bob))
              val lines = csv.split("\n").toList
              assertTrue(
                lines.head == "name,age,active,salary",
                lines(1)   == "Alice,30,true,75000.0",
                lines(2)   == "Bob,25,false,55000.0"
              )
            },
            test("encode with empty list produces only the header line") {
              assertTrue(enc.encode(Nil) == "name,age,active,salary")
            }
          ),
          suite("CsvDecoder")(
            test("decode reconstructs a single row") {
              val csv = "name,age,active,salary\nAlice,30,true,75000.0"
              assertTrue(dec.decode(csv) == Right(List(alice)))
            },
            test("decode reconstructs multiple rows") {
              val csv = enc.encode(List(alice, bob, charlie))
              assertTrue(dec.decode(csv) == Right(List(alice, bob, charlie)))
            },
            test("decode returns Left for wrong column count") {
              val csv = "name,age,active,salary\nAlice,30"
              assertTrue(dec.decode(csv).isLeft)
            },
            test("decode returns Left for invalid Int") {
              val csv = "name,age,active,salary\nAlice,notAnInt,true,75000.0"
              assertTrue(dec.decode(csv).isLeft)
            },
            test("decode returns Left for invalid Boolean") {
              val csv = "name,age,active,salary\nAlice,30,maybe,75000.0"
              assertTrue(dec.decode(csv).isLeft)
            },
            test("decode returns Left for invalid Double") {
              val csv = "name,age,active,salary\nAlice,30,true,notADouble"
              assertTrue(dec.decode(csv).isLeft)
            },
            test("decode with header-only CSV returns empty list") {
              assertTrue(dec.decode("name,age,active,salary") == Right(Nil))
            }
          ),
          suite("round-trip")(
            test("encode then decode returns original values") {
              val employees = List(alice, bob, charlie)
              assertTrue(dec.decode(enc.encode(employees)) == Right(employees))
            },
            test("round-trip preserves all primitive field types") {
              val original = Employee("D'Artagnan", 33, active = true, salary = 99999.99)
              assertTrue(dec.decode(enc.encode(List(original))) == Right(List(original)))
            }
          ),
          suite("Schema internals")(
            test("toDynamic produces Record with ordered named fields") {
              val dv = Employee.schema.toDynamic(alice)
              assertTrue(
                dv == DynamicValue.Record(
                  List(
                    "name"   -> DynamicValue.Primitive("Alice"),
                    "age"    -> DynamicValue.Primitive("30"),
                    "active" -> DynamicValue.Primitive("true"),
                    "salary" -> DynamicValue.Primitive("75000.0")
                  )
                )
              )
            },
            test("fromDynamic reconstructs Employee from Record") {
              val dv = DynamicValue.Record(
                List(
                  "name"   -> DynamicValue.Primitive("Alice"),
                  "age"    -> DynamicValue.Primitive("30"),
                  "active" -> DynamicValue.Primitive("true"),
                  "salary" -> DynamicValue.Primitive("75000.0")
                )
              )
              assertTrue(Employee.schema.fromDynamic(dv) == Right(alice))
            },
            test("fromDynamic returns Left for missing field") {
              val dv = DynamicValue.Record(
                List(
                  "name" -> DynamicValue.Primitive("Alice"),
                  "age"  -> DynamicValue.Primitive("30")
                )
              )
              assertTrue(Employee.schema.fromDynamic(dv).isLeft)
            },
            test("primitive int schema round-trips") {
              val s  = Schema.int
              val dv = s.toDynamic(42)
              assertTrue(s.fromDynamic(dv) == Right(42))
            },
            test("primitive double schema round-trips") {
              val s  = Schema.double
              val dv = s.toDynamic(3.14)
              assertTrue(s.fromDynamic(dv) == Right(3.14))
            },
            test("primitive boolean schema round-trips") {
              val s  = Schema.boolean
              assertTrue(
                s.fromDynamic(s.toDynamic(true))  == Right(true),
                s.fromDynamic(s.toDynamic(false)) == Right(false)
              )
            }
          ),
          suite("CsvCodec factory")(
            test("make returns Right for a record schema") {
              assertTrue(CsvCodec.make(Employee.schema).isRight)
            },
            test("encoder returns Left for a primitive schema") {
              assertTrue(CsvCodec.encoder(Schema.string).isLeft)
            },
            test("decoder returns Left for a primitive schema") {
              assertTrue(CsvCodec.decoder(Schema.int).isLeft)
            }
          )
        )
    }

    // ── Example Application ───────────────────────────────────────────────────

    object Exercise3Example extends ZIOAppDefault {

      val employees: List[Employee] = List(
        Employee("Alice",   30, active = true,  salary = 75000.0),
        Employee("Bob",     25, active = false, salary = 55000.0),
        Employee("Charlie", 45, active = true,  salary = 120000.0)
      )

      def run: ZIO[Any, Throwable, Unit] =
        for {
          enc     <- ZIO.fromEither(CsvCodec.encoder(Employee.schema)).mapError(new RuntimeException(_))
          dec     <- ZIO.fromEither(CsvCodec.decoder(Employee.schema)).mapError(new RuntimeException(_))

          _       <- Console.printLine("=== CSV Codec Example ===\n")

          csv      = enc.encode(employees)
          _       <- Console.printLine("Encoded CSV:")
          _       <- Console.printLine(csv)

          _       <- Console.printLine("\nDecoded back:")
          decoded <- ZIO.fromEither(dec.decode(csv)).mapError(new RuntimeException(_))
          _       <- ZIO.foreach(decoded)(e => Console.printLine(s"  $e"))

          _       <- Console.printLine(s"\nRound-trip successful: ${decoded == employees}")
        } yield ()
    }
  }

}
