package zionomicon.solutions

package SchemaTheAnatomyOfDataTypes {

  /**
   *   1. Extend the query DSL with additional comparison operators.
   */
  package QueryDslExtendedOperators {

    import zio._
    import zio.schema._
    import zio.schema.DeriveSchema
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

      /**
       * Derive a FieldAccessor from a ZIO Schema field.
       * Uses the schema's TypeId name as the record type and the field's own
       * name and getter — no manual wiring needed.
       */
      def fromSchemaRecord[S, A](
        record: Schema.Record[S],
        name: String
      ): FieldAccessor[S, A] = {
        val field = record.fields
          .find(_.name == name)
          .getOrElse(throw new NoSuchElementException(
            s"Field '$name' not found in schema '${record.id.name}'"
          ))
        new FieldAccessor[S, A](
          record.id.name,
          field.name,
          s => field.get(s).asInstanceOf[A]
        )
      }

      implicit class StringOps[S](val self: FieldAccessor[S, String])
          extends AnyVal {

        def contains(value: String): Query[S] =
          Query.Contains(self, value)
      }
    }

    // ── AccessorBuilder for Query DSL ──────────────────────────────────────
    /**
     * Builds FieldAccessors from schema records using ZIO Schema's machinery.
     * This accessor builder extracts field metadata and getter functions from
     * the schema, eliminating the need for manual accessor construction.
     */
    object QueryDslAccessorBuilder extends AccessorBuilder {
      override type Lens[F, S, A] = FieldAccessor[S, A]
      override type Prism[F, S, A] = Unit
      override type Traversal[S, A] = Unit

      override def makeLens[F, S, A](
        product: Schema.Record[S],
        term: Schema.Field[S, A]
      ): Lens[F, S, A] =
        FieldAccessor[S, A](
          product.id.name,
          term.name,
          s => term.get(s).asInstanceOf[A]
        )

      override def makePrism[F, S, A](
        sum: Schema.Enum[S],
        term: Schema.Case[S, A]
      ): Prism[F, S, A] = ()

      override def makeTraversal[S, A](
        collection: Schema.Collection[S, A],
        element: Schema[A]
      ): Traversal[S, A] = ()
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
      implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
      private val record: Schema.Record[Person] =
        schema.asInstanceOf[Schema.Record[Person]]

      private def buildAccessor[A](fieldName: String): FieldAccessor[Person, A] =
        QueryDslAccessorBuilder.makeLens[Any, Person, A](
          record,
          record.fields.find(_.name == fieldName).get.asInstanceOf[Schema.Field[Person, A]]
        )

      val name: FieldAccessor[Person, String] = buildAccessor("name")
      val age: FieldAccessor[Person, Int] = buildAccessor("age")
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
  package NestedQuerySupport {

    import zio._
    import zio.schema._
    import zio.schema.DeriveSchema
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

      def fromSchemaRecord[S, A](
        record: Schema.Record[S],
        name: String
      ): FieldAccessor[S, A] = {
        val field = record.fields
          .find(_.name == name)
          .getOrElse(throw new NoSuchElementException(
            s"Field '$name' not found in schema '${record.id.name}'"
          ))
        new FieldAccessor[S, A](
          record.id.name,
          field.name,
          s => field.get(s).asInstanceOf[A]
        )
      }

      implicit class StringOps[S](val self: FieldAccessor[S, String])
          extends AnyVal {

        def contains(value: String): Query[S] =
          Query.Contains(self, value)
      }
    }

    // ── AccessorBuilder for Query DSL ──────────────────────────────────────
    /**
     * Builds FieldAccessors from schema records using ZIO Schema's machinery.
     * This accessor builder extracts field metadata and getter functions from
     * the schema, eliminating the need for manual accessor construction.
     */
    object QueryDslAccessorBuilder extends AccessorBuilder {
      override type Lens[F, S, A] = FieldAccessor[S, A]
      override type Prism[F, S, A] = Unit
      override type Traversal[S, A] = Unit

      override def makeLens[F, S, A](
        product: Schema.Record[S],
        term: Schema.Field[S, A]
      ): Lens[F, S, A] =
        FieldAccessor[S, A](
          product.id.name,
          term.name,
          s => term.get(s).asInstanceOf[A]
        )

      override def makePrism[F, S, A](
        sum: Schema.Enum[S],
        term: Schema.Case[S, A]
      ): Prism[F, S, A] = ()

      override def makeTraversal[S, A](
        collection: Schema.Collection[S, A],
        element: Schema[A]
      ): Traversal[S, A] = ()
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
      implicit val schema: Schema[Address] = DeriveSchema.gen[Address]
      private val record: Schema.Record[Address] =
        schema.asInstanceOf[Schema.Record[Address]]

      private def buildAccessor[A](fieldName: String): FieldAccessor[Address, A] =
        QueryDslAccessorBuilder.makeLens[Any, Address, A](
          record,
          record.fields.find(_.name == fieldName).get.asInstanceOf[Schema.Field[Address, A]]
        )

      val country: FieldAccessor[Address, String] = buildAccessor("country")
      val city: FieldAccessor[Address, String] = buildAccessor("city")
      val street: FieldAccessor[Address, String] = buildAccessor("street")
    }

    case class Person(name: String, age: Int, address: Address)

    object Person {
      implicit val schema: Schema[Person] = DeriveSchema.gen[Person]
      private val record: Schema.Record[Person] =
        schema.asInstanceOf[Schema.Record[Person]]

      private def buildAccessor[A](fieldName: String): FieldAccessor[Person, A] =
        QueryDslAccessorBuilder.makeLens[Any, Person, A](
          record,
          record.fields.find(_.name == fieldName).get.asInstanceOf[Schema.Field[Person, A]]
        )

      val name: FieldAccessor[Person, String] = buildAccessor("name")
      val age: FieldAccessor[Person, Int] = buildAccessor("age")
      val address: FieldAccessor[Person, Address] = buildAccessor("address")
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
    import zio.schema._
    import zio.schema.DeriveSchema
    import zio.test._
    import scala.collection.immutable.ListMap

    // ── CsvEncoder[A] ────────────────────────────────────────────────────────

    trait CsvEncoder[A] {
      def headers: List[String]
      def encodeRow(value: A): List[String]

      def encode(values: List[A]): String =
        (headers.mkString(",") :: values.map(v => encodeRow(v).mkString(","))).mkString("\n")
    }

    // ── CsvDecoder[A] ────────────────────────────────────────────────────────

    trait CsvDecoder[A] {
      def decode(csv: String): Either[String, List[A]]
    }

    // ── CsvCodec factory ──────────────────────────────────────────────────────
    //
    // Derives encoder and decoder from Schema[A] using ZIO Schema primitives.
    //
    // Encoding path: A  →  schema.toDynamic  →  DynamicValue.Record
    //                →  extract Primitive.value.toString per field  →  CSV row
    //
    // Decoding path: CSV row  →  split by comma  →  build DynamicValue.Record
    //                (parsing each string via the field's Schema[_])
    //                →  schema.fromDynamic  →  A

    object CsvCodec {

      private def traverseEither[A, B](
        list: List[A]
      )(f: A => Either[String, B]): Either[String, List[B]] =
        list.foldRight[Either[String, List[B]]](Right(Nil)) { (a, acc) =>
          for { rest <- acc; b <- f(a) } yield b :: rest
        }

      /** Render a DynamicValue.Primitive as its CSV string. */
      private def primitiveToString(dv: DynamicValue): Either[String, String] = dv match {
        case DynamicValue.Primitive(value, _) => Right(value.toString)
        case _ => Left("CSV only supports flat records; nested values are not supported")
      }

      /**
       * ZIO Schema wraps field schemas in Schema.Lazy when deriving case class
       * schemas (to support recursive types).  Forcing the lazy here ensures
       * the inner Schema.Primitive is visible to the pattern match below.
       */
      private def forceSchema(s: Schema[_]): Schema[_] = s match {
        case l: Schema.Lazy[_] => l.schema
        case other             => other
      }

      /**
       * Parse a raw CSV string into a DynamicValue.Primitive using the field's
       * Schema to determine the expected type.  Calls schema.fromDynamic later
       * so the type tag must match what ZIO Schema produced during toDynamic.
       */
      private def stringToPrimitive(
        s: String,
        fieldSchema: Schema[_]
      ): Either[String, DynamicValue] =
        forceSchema(fieldSchema) match {
          case Schema.Primitive(st, _) =>
            st match {
              case StandardType.StringType =>
                Right(DynamicValue.Primitive(s, StandardType.StringType))
              case StandardType.IntType =>
                s.toIntOption.toRight(s"Not an Int: '$s'")
                  .map(DynamicValue.Primitive(_, StandardType.IntType))
              case StandardType.DoubleType =>
                s.toDoubleOption.toRight(s"Not a Double: '$s'")
                  .map(DynamicValue.Primitive(_, StandardType.DoubleType))
              case StandardType.BoolType =>
                s match {
                  case "true"  => Right(DynamicValue.Primitive(true, StandardType.BoolType))
                  case "false" => Right(DynamicValue.Primitive(false, StandardType.BoolType))
                  case _       => Left(s"Not a Boolean: '$s'")
                }
              case _ => Left(s"Unsupported CSV primitive type: $st")
            }
          case _ => Left("CSV only supports primitive field types")
        }

      def encoder[A](implicit schema: Schema[A]): Either[String, CsvEncoder[A]] =
        schema match {
          case record: Schema.Record[A] =>
            Right(new CsvEncoder[A] {
              val headers: List[String] = record.fields.map(_.name).toList

              def encodeRow(value: A): List[String] =
                schema.toDynamic(value) match {
                  case DynamicValue.Record(_, fieldValues) =>
                    headers.map(h =>
                      fieldValues.get(h).flatMap(primitiveToString(_).toOption).getOrElse("")
                    )
                  case _ =>
                    sys.error("toDynamic returned non-Record for a record schema")
                }
            })
          case _ => Left("CsvEncoder requires a record schema")
        }

      def decoder[A](implicit schema: Schema[A]): Either[String, CsvDecoder[A]] =
        schema match {
          case record: Schema.Record[A] =>
            val fieldSchemaByName: Map[String, Schema[_]] =
              record.fields.map(f => f.name -> (f.schema: Schema[_])).toList.toMap
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
                        Left(s"Expected ${parsedHeaders.length} columns, got ${values.length}")
                      else
                        for {
                          dvFields <- traverseEither(parsedHeaders.zip(values)) {
                            case (name, s) =>
                              fieldSchemaByName
                                .get(name)
                                .toRight(s"Unknown field: '$name'")
                                .flatMap(stringToPrimitive(s, _))
                                .map(dv => name -> dv)
                          }
                          decoded  <- schema.fromDynamic(
                            DynamicValue.Record(record.id, ListMap(dvFields: _*))
                          )
                        } yield decoded
                    }
                }
              }
            })
          case _ => Left("CsvDecoder requires a record schema")
        }

      def make[A](implicit
        schema: Schema[A]
      ): Either[String, (CsvEncoder[A], CsvDecoder[A])] =
        for {
          enc <- encoder[A]
          dec <- decoder[A]
        } yield (enc, dec)
    }

    // ── Domain model & schema ─────────────────────────────────────────────────

    case class Employee(name: String, age: Int, active: Boolean, salary: Double)

    object Employee {
      implicit val schema: Schema[Employee] = DeriveSchema.gen[Employee]
    }

    // ── Spec ──────────────────────────────────────────────────────────────────

    object CsvCodecSpec extends ZIOSpecDefault {

      val alice: Employee   = Employee("Alice",   30, active = true,  salary = 75000.0)
      val bob: Employee     = Employee("Bob",     25, active = false, salary = 55000.0)
      val charlie: Employee = Employee("Charlie", 45, active = true,  salary = 120000.0)

      val enc: CsvEncoder[Employee] = CsvCodec.encoder[Employee].toOption.get
      val dec: CsvDecoder[Employee] = CsvCodec.decoder[Employee].toOption.get

      private val employeeRecord: Schema.Record[Employee] =
        Employee.schema.asInstanceOf[Schema.Record[Employee]]

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
          suite("ZIO Schema integration")(
            test("toDynamic produces Record with typed primitive values") {
              val dv = Employee.schema.toDynamic(alice)
              assertTrue(
                dv == DynamicValue.Record(
                  employeeRecord.id,
                  ListMap(
                    "name"   -> DynamicValue.Primitive("Alice",   StandardType.StringType),
                    "age"    -> DynamicValue.Primitive(30,        StandardType.IntType),
                    "active" -> DynamicValue.Primitive(true,      StandardType.BoolType),
                    "salary" -> DynamicValue.Primitive(75000.0,   StandardType.DoubleType)
                  )
                )
              )
            },
            test("fromDynamic reconstructs Employee from Record") {
              val dv = DynamicValue.Record(
                employeeRecord.id,
                ListMap(
                  "name"   -> DynamicValue.Primitive("Alice",   StandardType.StringType),
                  "age"    -> DynamicValue.Primitive(30,        StandardType.IntType),
                  "active" -> DynamicValue.Primitive(true,      StandardType.BoolType),
                  "salary" -> DynamicValue.Primitive(75000.0,   StandardType.DoubleType)
                )
              )
              assertTrue(Employee.schema.fromDynamic(dv) == Right(alice))
            },
            test("fromDynamic returns Left for missing field") {
              val dv = DynamicValue.Record(
                employeeRecord.id,
                ListMap(
                  "name" -> DynamicValue.Primitive("Alice", StandardType.StringType),
                  "age"  -> DynamicValue.Primitive(30,      StandardType.IntType)
                )
              )
              assertTrue(Employee.schema.fromDynamic(dv).isLeft)
            },
            test("Schema[Int] round-trips via toDynamic / fromDynamic") {
              val s  = Schema[Int]
              val dv = s.toDynamic(42)
              assertTrue(s.fromDynamic(dv) == Right(42))
            },
            test("Schema[Double] round-trips via toDynamic / fromDynamic") {
              val s  = Schema[Double]
              val dv = s.toDynamic(3.14)
              assertTrue(s.fromDynamic(dv) == Right(3.14))
            },
            test("Schema[Boolean] round-trips via toDynamic / fromDynamic") {
              val s = Schema[Boolean]
              assertTrue(
                s.fromDynamic(s.toDynamic(true))  == Right(true),
                s.fromDynamic(s.toDynamic(false)) == Right(false)
              )
            }
          ),
          suite("CsvCodec factory")(
            test("make returns Right for a record schema") {
              assertTrue(CsvCodec.make[Employee].isRight)
            },
            test("encoder returns Left for a primitive schema") {
              assertTrue(CsvCodec.encoder(Schema[String]).isLeft)
            },
            test("decoder returns Left for a primitive schema") {
              assertTrue(CsvCodec.decoder(Schema[Int]).isLeft)
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
          enc     <- ZIO.fromEither(CsvCodec.encoder[Employee]).mapError(new RuntimeException(_))
          dec     <- ZIO.fromEither(CsvCodec.decoder[Employee]).mapError(new RuntimeException(_))

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
