package zionomicon.exercises

package SchemaTheAnatomyOfDataTypes {

  /**
   *   1. Extend the query DSL with additional comparison operators.
   *
   * The query DSL from this chapter currently supports only equality checks
   * with the `===` operator. Extend the `Query` sealed trait and
   * `FieldAccessor` to support the following operators:
   *   - `>` (greater than)
   *   - `<` (less than)
   *   - `>=` (greater than or equal)
   *   - `<=` (less than or equal)
   *   - `!==` (not equal)
   *   - `contains` (for string fields)
   *
   * Hint: You will need to add new case classes to the `Query` sealed trait for
   * each operator, and update the `FieldAccessor` class to provide these
   * operators as methods.
   */
  package QueryDslExtendedOperators {}

  /**
   *   2. Implement nested query support in the Query DSL.
   *
   * Update the query DSL to support nested queries for accessing fields of
   * nested data types. For example, given:
   *
   * {{{
   *      case class Address(country: String, city: String, street: String)
   *      case class Person(name: String, age: Int, address: Address)
   * }}}
   *
   * You should be able to write queries like:
   *   - `(Person.name === "John") && ((Person.address / Address.city) === "Tokyo")`
   *
   * Update the `FieldAccessor` and `Query` types to support path-based field
   * access through nested structures using `/` composition for path navigation.
   */
  package NestedQuerySupport {}

  /**
   *   3. Write a CSV codec that supports encoding and decoding of record types.
   *
   * Create a CSV codec that:
   *   - Supports encoding simple record types (case classes) to CSV format
   *   - Supports decoding CSV data back to typed record instances
   *   - Handles primitive field types: String, Int, Boolean, and Double
   *   - Generates appropriate CSV headers from field names
   *   - Uses commas as field delimiters and newlines as record delimiters
   *
   * Hint: Use ZIO Schema's ability to convert values to/from `DynamicValue` to
   * implement this codec. You can follow the same pattern as the JSON codec
   * shown in the chapter, using `schema.toDynamic` for encoding and
   * `schema.fromDynamic` for decoding.
   */
  package CsvCodec {}

}
