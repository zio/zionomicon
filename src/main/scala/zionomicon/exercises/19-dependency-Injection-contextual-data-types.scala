package zionomicon.exercises

package DependencyInjectionContextualDataTypes {

  /**
   *   1. Implement a simple transactional key-value store that:
   *      \index{Transactional Key-value Store}
   *
   *   - Supports basic operations (get, put, delete)
   *   - Uses ZIO environment to mark transactional effects
   *   - Provides atomic execution of multiple operations
   *   - Implements proper rollback on failure
   *
   * Sample interface:
   *
   * {{{
   * trait KVStore[k, V] {
   *   def get[V](key: K): ZIO[Transaction, Throwable, Option[V]]
   *   def put(key: K, value: V): ZIO[Transaction, Throwable, Unit]
   *   def delete(key: K): ZIO[Transaction, Throwable, Unit]
   * }
   * }}}
   */
  package TransactionalKeyValueStoreImpl {}

  /**
   *   2. Implement a distributed transaction manager using the Saga pattern
   *      discussed in the chapter by encoding the transaction into the ZIO
   *      environment. Each business action must have a corresponding
   *      compensating action that can roll back its effects. The transaction
   *      manager should compose these action-compensation pairs into a single
   *      atomic transaction, ensuring that either all actions are completed
   *      successfully or all completed actions are rolled back through their
   *      compensating actions.
   */
  package DistributedTransactionManagerImpl {}

}
