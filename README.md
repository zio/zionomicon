# Zionomicon Solutions

A comprehensive collection of **exercises and solutions** for learning [ZIO](https://zio.dev/) — a Scala library for asynchronous and concurrent programming.

This repository serves as a hands-on companion to the Zionomicon Book, providing solutions to the exercises in each chapter.

## What is ZIO?

ZIO is a zero-dependency Scala library that provides:
- **Powerful async/concurrent runtime** built on fibers (lightweight threads)
- **Functional error handling** with typed error channels
- **Resource safety** with automatic cleanup guarantees
- **Dependency injection** through type-level composition
- **Software Transactional Memory (STM)** for safe concurrent access

## How to Use This Repository

1. **Start with exercises**: Look in `src/main/scala/zionomicon/exercises/` for skeleton code with problem statements in Scaladoc comments
2. **Attempt the exercises**: Try to implement the solutions yourself
3. **Check solutions**: Compare your work against `src/main/scala/zionomicon/solutions/`
4. **Read explanations**: Each solution includes detailed comments explaining the approach and key concepts

## Overview

### Fundamentals (Chapters 1–3)
- **Chapter 1**: First steps with ZIO — basic effects, main function, error handling ([Exercises](src/main/scala/zionomicon/exercises/01-first-steps-with-zio.scala) | [Solutions](src/main/scala/zionomicon/solutions/01-first-steps-with-zio.scala))
- **Chapter 2**: Testing ZIO programs — test assertions, test services ([Exercises](src/main/scala/zionomicon/exercises/02-testing-zio-programs.scala) | [Solutions](src/main/scala/zionomicon/solutions/02-testing-zio-programs.scala))
- **Chapter 3**: The ZIO error model — typed errors, exception handling ([Exercises](src/main/scala/zionomicon/exercises/03-the-zio-error-model.scala) | [Solutions](src/main/scala/zionomicon/solutions/03-the-zio-error-model.scala))

### Parallelism & Concurrency (Chapters 5–6, 8)
- **Chapter 5**: The fiber model — lightweight concurrency, fiber creation and inspection ([Exercises](src/main/scala/zionomicon/exercises/05-parallelism-and-concurrency-the-fiber-model.scala) | [Solutions](src/main/scala/zionomicon/solutions/05-parallelism-and-concurrency-the-fiber-model.scala))
- **Chapter 6**: Concurrent operators — race, zip, foreach parallel execution ([Exercises](src/main/scala/zionomicon/exercises/06-parallelism-and-concurrency-operators.scala) | [Solutions](src/main/scala/zionomicon/solutions/06-parallelism-and-concurrency-operators.scala))
- **Chapter 8**: Interruption in depth — cancellation, finalizers, safe shutdown ([Exercises](src/main/scala/zionomicon/exercises/08-parallelism-and-concurrency-interruption-in-depth.scala) | [Solutions](src/main/scala/zionomicon/solutions/08-parallelism-and-concurrency-interruption-in-depth.scala))

### Concurrent Data Structures (Chapters 9–15)
- **Chapter 9**: Ref — shared mutable state with atomic operations ([Exercises](src/main/scala/zionomicon/exercises/09-concurrent-structures-ref-shared-state.scala) | [Solutions](src/main/scala/zionomicon/solutions/09-concurrent-structures-ref-shared-state.scala))
- **Chapter 10**: Promise — one-time value publication and waiting ([Exercises](src/main/scala/zionomicon/exercises/10-concurrent-structures-promise-work-synchronization.scala) | [Solutions](src/main/scala/zionomicon/solutions/10-concurrent-structures-promise-work-synchronization.scala))
- **Chapter 11**: Queue — FIFO work distribution between fibers ([Exercises](src/main/scala/zionomicon/exercises/11-concurrent-structures-queue-work-distribution.scala) | [Solutions](src/main/scala/zionomicon/solutions/11-concurrent-structures-queue-work-distribution.scala))
- **Chapter 12**: Hub — broadcasting messages to multiple subscribers ([Exercises](src/main/scala/zionomicon/exercises/12-concurrent-structures-hub-broadcasting.scala) | [Solutions](src/main/scala/zionomicon/solutions/12-concurrent-structures-hub-broadcasting.scala))
- **Chapter 13**: Semaphore — rate limiting and work constraints ([Exercises](src/main/scala/zionomicon/exercises/13-concurrent-structures-semaphore-work-limiting.scala) | [Solutions](src/main/scala/zionomicon/solutions/13-concurrent-structures-semaphore-work-limiting.scala))
- **Chapter 14**: Acquire/release — safe resource handling in async code ([Exercises](src/main/scala/zionomicon/exercises/14-acquire-release-safe-resource-handling-for-asynchronous-code.scala) | [Solutions](src/main/scala/zionomicon/solutions/14-acquire-release-safe-resource-handling-for-asynchronous-code.scala))
- **Chapter 15**: Scope — composable resource management ([Exercises](src/main/scala/zionomicon/exercises/15-scope-composable-resources.scala) | [Solutions](src/main/scala/zionomicon/solutions/15-scope-composable-resources.scala))

### Dependency Injection & Configuration (Chapters 17, 19–20)
- **Chapter 17**: Dependency injection essentials — R type parameter, layers ([Exercises](src/main/scala/zionomicon/exercises/17-dependency-injection-essentials.scala) | [Solutions](src/main/scala/zionomicon/solutions/17-dependency-injection-essentials.scala))
- **Chapter 19**: Contextual data types — ZIO environment composition ([Exercises](src/main/scala/zionomicon/exercises/19-dependency-Injection-contextual-data-types.scala) | [Solutions](src/main/scala/zionomicon/solutions/19-dependency-Injection-contextual-data-types.scala))
- **Chapter 20**: Configuring ZIO applications — configuration management ([Exercises](src/main/scala/zionomicon/exercises/20-configuring-zio-applications.scala) | [Solutions](src/main/scala/zionomicon/solutions/20-configuring-zio-applications.scala))

### Software Transactional Memory (Chapters 21–23)
- **Chapter 21**: STM composing atomicity — transactional effects ([Exercises](src/main/scala/zionomicon/exercises/21-stm-composing-atomicity.scala) | [Solutions](src/main/scala/zionomicon/solutions/21-stm-composing-atomicity.scala))
- **Chapter 22**: STM data structures — TArray, TMap, TQueue, etc. ([Exercises](src/main/scala/zionomicon/exercises/22-stm-data-structures.scala) | [Solutions](src/main/scala/zionomicon/solutions/22-stm-data-structures.scala))
- **Chapter 23**: STM performance — optimization and tradeoffs ([Exercises](src/main/scala/zionomicon/exercises/23-stm-performance.scala) | [Solutions](src/main/scala/zionomicon/solutions/23-stm-performance.scala))

### Resilience & Practices (Chapters 24, 26)
- **Chapter 24**: Retries — schedules, exponential backoff, adaptive policies ([Exercises](src/main/scala/zionomicon/exercises/24-retries.scala) | [Solutions](src/main/scala/zionomicon/solutions/24-retries.scala))
- **Chapter 26**: Best practices — design patterns and code organization ([Exercises](src/main/scala/zionomicon/exercises/26-best-practices.scala) | [Solutions](src/main/scala/zionomicon/solutions/26-best-practices.scala))

## Building and Running

### Prerequisites
- Scala 2.13
- SBT (Simple Build Tool)

### Compile
```bash
sbt compile
```

### Run Tests
```bash
sbt test
```

### Run Benchmarks
```bash
sbt jmh:run
```

## Dependencies

The project uses:
- **ZIO 2.1.21** — Core async/concurrent runtime
- **zio-config** — Configuration management
- **zio-http** — HTTP server/client utilities
- **zio-test** — Testing framework
- **zio-prelude** — Validation and type safety utilities
- **doobie** — Database access (with SQLite driver)
- **JMH** — Benchmarking (Java Microbenchmark Harness)

See `build.sbt` for full dependency list.

## Contributing

This is a learning resource maintained in sync with the upstream [Zionomicon](https://github.com/zio/zionomicon). Contributions are welcome — fixes, additional exercises, and clarifications improve the learning experience for everyone.
