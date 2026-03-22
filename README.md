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
- **Chapter 1**: First steps with ZIO — basic effects, main function, error handling
- **Chapter 2**: Testing ZIO programs — test assertions, test services
- **Chapter 3**: The ZIO error model — typed errors, exception handling

### Parallelism & Concurrency (Chapters 5–6, 8)
- **Chapter 5**: The fiber model — lightweight concurrency, fiber creation and inspection
- **Chapter 6**: Concurrent operators — race, zip, foreach parallel execution
- **Chapter 8**: Interruption in depth — cancellation, finalizers, safe shutdown

### Concurrent Data Structures (Chapters 9–15)
- **Chapter 9**: Ref — shared mutable state with atomic operations
- **Chapter 10**: Promise — one-time value publication and waiting
- **Chapter 11**: Queue — FIFO work distribution between fibers
- **Chapter 12**: Hub — broadcasting messages to multiple subscribers
- **Chapter 13**: Semaphore — rate limiting and work constraints
- **Chapter 14**: Acquire/release — safe resource handling in async code
- **Chapter 15**: Scope — composable resource management

### Dependency Injection & Configuration (Chapters 17, 19–20)
- **Chapter 17**: Dependency injection essentials — R type parameter, layers
- **Chapter 19**: Contextual data types — ZIO environment composition
- **Chapter 20**: Configuring ZIO applications — configuration management

### Software Transactional Memory (Chapters 21–23)
- **Chapter 21**: STM composing atomicity — transactional effects
- **Chapter 22**: STM data structures — TArray, TMap, TQueue, etc.
- **Chapter 23**: STM performance — optimization and tradeoffs

### Resilience & Practices (Chapters 24, 26)
- **Chapter 24**: Retries — schedules, exponential backoff, adaptive policies
- **Chapter 26**: Best practices — design patterns and code organization

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
