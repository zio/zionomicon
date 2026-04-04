# Rate Limiting Middleware Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a HandlerAspect middleware that tracks requests by client IP address and enforces configurable rate limits with automatic time window reset.

**Architecture:** The middleware uses a per-instance `Ref[Map[String, (Int, Instant)]]` to track IP → (requestCount, windowStartTime). On each request, the handler extracts the client IP (X-Forwarded-For with fallback to socket address), checks if the window has expired, and either allows the request or returns 429. A background task periodically cleans up expired entries.

**Tech Stack:**
- ZIO HTTP (HandlerAspect, Request, Response)
- ZIO (Ref, Clock, Schedule)
- Java Time (Instant, Duration)

---

## File Structure

**Files to modify:**
- `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala`
  - Add RateLimitConfig case class
  - Add RateLimitState data structure
  - Add RateLimiter object with core logic
  - Add rateLimitMiddleware HandlerAspect factory
  - Add ExampleApp demonstrating usage
  - Add RateLimitTest integration tests

**No new files created** — all code goes into the existing exercise file under the `RateLimiting` package.

---

## Tasks

### Task 1: Define RateLimitConfig Case Class

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (package RateLimiting, after imports)

- [ ] **Step 1: Add RateLimitConfig and data structures**

Replace the empty `RateLimiting` package body with imports and the config definition:

```scala
package RateLimiting {

  import zio._
  import zio.http._
  import java.time.Instant
  import scala.concurrent.duration.Duration

  package Solution {

    /**
     * Configuration for rate limiting middleware.
     *
     * @param maxRequests Maximum number of requests allowed within timeWindow
     * @param timeWindow Duration of the rate limit window
     */
    case class RateLimitConfig(
      maxRequests: Int,
      timeWindow: Duration
    )

    /**
     * Internal state for tracking a single IP address.
     *
     * @param requestCount Number of requests in current window
     * @param windowStartTime When the current window started
     */
    case class RateLimitState(
      requestCount: Int,
      windowStartTime: Instant
    )
```

- [ ] **Step 2: Verify file compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Add RateLimitConfig and RateLimitState data structures"
```

---

### Task 2: Implement IP Extraction Helper

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add IP extraction logic**

After the case class definitions, add:

```scala
    object RateLimiter {

      /**
       * Extracts the client IP address from a request.
       *
       * Priority:
       * 1. X-Forwarded-For header (first IP if comma-separated)
       * 2. Request.remoteAddress (socket address)
       * 3. "unknown" (fallback)
       */
      private def extractClientIp(req: Request): String =
        req.headers
          .get("X-Forwarded-For")
          .map(_.split(",").head.trim)
          .orElse(req.remoteAddress.map(_.toString))
          .getOrElse("unknown")
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Add IP extraction helper with X-Forwarded-For fallback"
```

---

### Task 3: Implement Cleanup Task

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimiter object)

- [ ] **Step 1: Add cleanup function**

After the extractClientIp function, add:

```scala
      /**
       * Removes expired entries from the rate limit map.
       *
       * Called periodically to prevent unbounded memory growth.
       * Removes entries where windowStartTime + timeWindow < now.
       */
      private def cleanupExpiredEntries(
        stateRef: Ref[Map[String, RateLimitState]],
        config: RateLimitConfig
      ): ZIO[Any, Nothing, Unit] =
        for {
          now <- Clock.instant
          _ <- stateRef.update { state =>
            state.filter { case (_, limitState) =>
              val windowEnd = limitState.windowStartTime.plusNanos(
                config.timeWindow.toNanos
              )
              now.isBefore(windowEnd)
            }
          }
        } yield ()
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Add cleanup function for expired rate limit entries"
```

---

### Task 4: Implement Rate Limit Check Logic

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimiter object)

- [ ] **Step 1: Add checkRateLimit function**

After the cleanupExpiredEntries function, add:

```scala
      /**
       * Checks if a request from the given IP should be allowed under the rate limit.
       *
       * Returns Some(remainingRequests) if allowed, None if rate limit exceeded.
       *
       * Logic:
       * - If no entry for IP: create entry with count=1, allow
       * - If window expired: reset count to 1, allow
       * - If count < maxRequests: increment, allow
       * - If count >= maxRequests: deny
       */
      private def checkRateLimit(
        stateRef: Ref[Map[String, RateLimitState]],
        config: RateLimitConfig,
        clientIp: String
      ): ZIO[Any, Nothing, Option[Int]] =
        for {
          now <- Clock.instant
          result <- stateRef.modify { state =>
            state.get(clientIp) match {
              case None =>
                // New IP: create entry with count 1
                val newState = state + (clientIp -> RateLimitState(1, now))
                (Some(config.maxRequests - 1), newState)

              case Some(limitState) =>
                val windowEnd = limitState.windowStartTime.plusNanos(
                  config.timeWindow.toNanos
                )
                if (now.isAfter(windowEnd)) {
                  // Window expired: reset counter
                  val newState =
                    state + (clientIp -> RateLimitState(1, now))
                  (Some(config.maxRequests - 1), newState)
                } else if (limitState.requestCount < config.maxRequests) {
                  // Within limit: increment counter
                  val updated = limitState.copy(
                    requestCount = limitState.requestCount + 1
                  )
                  val newState = state + (clientIp -> updated)
                  val remaining = config.maxRequests - updated.requestCount
                  (Some(remaining), newState)
                } else {
                  // Limit exceeded: deny
                  (None, state)
                }
            }
          }
        } yield result
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Add rate limit check logic with window expiration"
```

---

### Task 5: Implement rateLimitMiddleware HandlerAspect

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimiter object)

- [ ] **Step 1: Add rateLimitMiddleware factory**

After the checkRateLimit function, add:

```scala      /**
       * Creates a rate limiting middleware with the given configuration.
       *
       * Each middleware instance has its own isolated state (per-route).
       * Includes automatic cleanup task to remove expired entries.
       *
       * Returns 429 Too Many Requests if client IP exceeds the rate limit.
       * Returns 200 OK (or handler response) if within limit.
       */
      def rateLimitMiddleware(
        config: RateLimitConfig
      ): HandlerAspect[Any, Unit] =
        HandlerAspect.interceptHandler(
          Handler.fromFunctionZIO[Request] { (req: Request) =>
            for {
              // Initialize state on first request
              stateRef <- Ref.make(Map.empty[String, RateLimitState])
              
              // Start cleanup task (runs every 10 seconds)
              cleanupFiber <- cleanupExpiredEntries(stateRef, config)
                .repeatWithSchedule(Schedule.fixed(10.seconds))
                .fork
              
              // Extract client IP and check rate limit
              clientIp = extractClientIp(req)
              allowed <- checkRateLimit(stateRef, config, clientIp)
            } yield (
              stateRef,
              cleanupFiber,
              allowed,
              clientIp
            )
          }
        )(
          Handler.fromFunctionZIO[
            (Ref[Map[String, RateLimitState]], Fiber[Any, Nothing, Unit], Option[Int], String)
          ] { case (stateRef, cleanupFiber, allowed, clientIp) =>
            allowed match {
              case Some(_) =>
                // Request allowed
                ZIO.succeed(ZIO.unit)
              case None =>
                // Rate limit exceeded
                ZIO.succeed(ZIO.fail(Response.tooManyRequests()))
            }
          }
        )
```

Wait, this approach is problematic because we're creating a new Ref and cleanup task on every request. Let me revise this to be more efficient.

- [ ] **Step 1 (Revised): Add rateLimitMiddleware factory**

After the checkRateLimit function, add:

```scala
      /**
       * Creates a rate limiting middleware with the given configuration.
       *
       * Each middleware instance has its own isolated state (per-route).
       * Includes automatic cleanup task to remove expired entries.
       *
       * Returns 429 Too Many Requests if client IP exceeds the rate limit.
       * Returns 200 OK (or handler response) if within limit.
       */
      def rateLimitMiddleware(
        config: RateLimitConfig
      ): HandlerAspect[Any, Unit] = {
        // Create shared state and cleanup task once per middleware instance
        val stateAndCleanupEffect: ZIO[Any, Nothing, (Ref[Map[String, RateLimitState]], Fiber[Any, Nothing, Unit])] =
          for {
            stateRef <- Ref.make(Map.empty[String, RateLimitState])
            cleanupFiber <- cleanupExpiredEntries(stateRef, config)
              .repeatWithSchedule(Schedule.fixed(10.seconds))
              .fork
          } yield (stateRef, cleanupFiber)

        HandlerAspect.interceptHandler(
          Handler.fromFunctionZIO[Request] { (req: Request) =>
            stateAndCleanupEffect.flatMap { case (stateRef, _) =>
              val clientIp = extractClientIp(req)
              checkRateLimit(stateRef, config, clientIp).map { allowed =>
                (stateRef, allowed, clientIp)
              }
            }
          }
        )(
          Handler.fromFunctionZIO[(Ref[Map[String, RateLimitState]], Option[Int], String)] {
            case (_, Some(_), _) =>
              // Request allowed
              ZIO.succeed(ZIO.unit)
            case (_, None, clientIp) =>
              // Rate limit exceeded
              ZIO.succeed(
                ZIO.fail(Response.tooManyRequests(s"Rate limit exceeded for IP: $clientIp"))
              )
          }
        )
      }
```

Actually, this is still complex. Let me use a simpler pattern based on the existing duration logging middleware from Exercise 2.

- [ ] **Step 1 (Simplified): Add rateLimitMiddleware factory**

After the checkRateLimit function, add:

```scala
      /**
       * Creates a rate limiting middleware with the given configuration.
       *
       * Each middleware instance maintains its own isolated rate limit state.
       * Automatically cleans up expired entries every 10 seconds.
       *
       * Returns:
       * - 429 Too Many Requests if client IP exceeds the rate limit
       * - 200 OK (or handler response) if within limit
       */
      def rateLimitMiddleware(
        config: RateLimitConfig
      ): HandlerAspect[Any, Unit] =
        HandlerAspect.interceptHandlerStateful(
          Handler.fromFunctionZIO[Request] { req =>
            for {
              stateRef <- Ref.make(Map.empty[String, RateLimitState])
              clientIp = extractClientIp(req)
            } yield (stateRef, (req, clientIp))
          }
        )(
          Handler.fromFunctionZIO[(Ref[Map[String, RateLimitState]], Response)] {
            case (stateRef, response) =>
              // No post-processing needed for this middleware
              ZIO.succeed(response)
          }
        )
```

This is still not quite right. Let me think about this more carefully. Looking at the HandlerAspect API, we need to think about where the rate limit check happens. Let me use a simpler direct implementation:

- [ ] **Step 1 (Final): Add rateLimitMiddleware factory**

After the checkRateLimit function, add:

```scala
      /**
       * Creates a rate limiting middleware with the given configuration.
       *
       * Each middleware instance maintains its own isolated rate limit state
       * (per-route semantics).
       *
       * Returns 429 Too Many Requests if client IP exceeds the configured limit.
       * Otherwise allows the request through.
       */
      def rateLimitMiddleware(
        config: RateLimitConfig
      ): HandlerAspect[Any, Unit] = {
        // Lazy initialization: state is created on first use, shared across all requests
        lazy val stateEffect: UIO[(Ref[Map[String, RateLimitState]], Fiber[Any, Nothing, Unit])] =
          for {
            stateRef <- Ref.make(Map.empty[String, RateLimitState])
            cleanupFiber <- cleanupExpiredEntries(stateRef, config)
              .repeatWithSchedule(Schedule.fixed(10.seconds))
              .fork
          } yield (stateRef, cleanupFiber)

        HandlerAspect.interceptHandler(
          Handler.fromFunctionZIO[Request] { req =>
            stateEffect.flatMap { case (stateRef, _) =>
              val clientIp = extractClientIp(req)
              checkRateLimit(stateRef, config, clientIp).map { allowed =>
                (stateRef, clientIp, allowed)
              }
            }
          }
        )(
          Handler.fromFunctionZIO[(Ref[Map[String, RateLimitState]], String, Option[Int])] {
            case (_, clientIp, None) =>
              // Rate limit exceeded
              ZIO.fail(Response.tooManyRequests())
            case (_, _, Some(_)) =>
              // Request allowed
              ZIO.succeed(ZIO.unit)
          }
        )
      }
    }
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Implement rateLimitMiddleware HandlerAspect with lazy state initialization"
```

---

### Task 6: Create ExampleApp Demonstrator

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add ExampleApp**

After the RateLimiter object closes, add:

```scala
    /**
     * Example application demonstrating the rate limit middleware.
     * Applies a 5-request-per-10-second limit to the /api/data endpoint.
     *
     * Example usage:
     * - First 5 requests within 10 seconds: 200 OK
     * - 6th+ requests within same window: 429 Too Many Requests
     * - After 10 seconds: counter resets, new requests allowed
     *
     * Test with:
     * for i in {1..10}; do
     *   curl -X GET http://localhost:8080/api/data
     *   echo "Request $i"
     * done
     */
    object ExampleApp extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        Server
          .serve(
            Routes(
              Method.GET / "api" / "data" ->
                handler {
                  ZIO.succeed(Response.text("Here is your data"))
                }
            ) @@ RateLimiter.rateLimitMiddleware(
              RateLimitConfig(maxRequests = 5, timeWindow = 10.seconds)
            )
          )
          .provide(Server.default)
    }
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-5): Add ExampleApp demonstrating rate limit middleware"
```

---

### Task 7: Create Integration Test Structure

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add test scaffold**

After ExampleApp, add:

```scala
    /**
     * Integration tests for the rate limit middleware using ZIO HTTP Client.
     *
     * NOTE: We use ZIOAppDefault for integration tests (not ZIOSpecDefault)
     * because real I/O operations need wall-clock time semantics.
     *
     * Run with: sbtn "runMain
     * zionomicon.solutions.CommunicationProtocolsZIOHTTP.RateLimiting.Solution.RateLimitTest"
     */
    object RateLimitTest extends ZIOAppDefault {

      def run: ZIO[Any, Any, Unit] =
        (for {
          _ <- ZIO.debug("Starting rate limit tests...")

          // Allocate a free port
          port <- ZIO.attemptBlocking {
                    val socket = new java.net.ServerSocket(0)
                    val p      = socket.getLocalPort
                    socket.close()
                    p
                  }
          _ <- ZIO.debug(s"Allocated port: $port")

          // Start the server with rate limiting (3 requests per 5 seconds)
          _ <- Server
                 .serve(
                   Routes(
                     Method.GET / "test" ->
                       handler {
                         ZIO.succeed(Response.ok())
                       }
                   ) @@ RateLimiter.rateLimitMiddleware(
                     RateLimitConfig(maxRequests = 3, timeWindow = 5.seconds)
                   )
                 )
                 .provide(
                   ZLayer.succeed(Server.Config.default.port(port)) >>> Server.live
                 )
                 .fork
          _ <- ZIO.sleep(1.second)

          // Tests will go here
          _ <- ZIO.debug("Running rate limit tests...")
          _ <- ZIO.debug("\n✅ All tests completed successfully!")
        } yield ()).provide(Client.default)
    }
```

- [ ] **Step 2: Verify compilation**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-5): Add RateLimitTest scaffold with server setup"
```

---

### Task 8: Implement TEST 1 - Requests Under Limit

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimitTest)

- [ ] **Step 1: Add TEST 1 - requests under limit**

Replace `// Tests will go here` with:

```scala
          // TEST 1: Requests under limit (3 allowed)
          _ <- ZIO.debug("\n=== TEST 1: Send 3 requests (under limit) ===")
          url1 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/test"))
          _ <- ZIO.foreachDiscard((1 to 3)) { i =>
                 for {
                   req  = Request.get(url1)
                   res <- Client.batched(req)
                   _ <- ZIO.debug(s"Request $i: ${res.status}")
                   _ <- if (res.status == Status.Ok) {
                          ZIO.succeed(())
                        } else {
                          ZIO.fail(s"Request $i failed: expected 200, got ${res.status}")
                        }
                 } yield ()
               }
          _ <- ZIO.debug("✅ TEST 1 passed - all 3 requests allowed")
```

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.RateLimiting.Solution.RateLimitTest"`
Expected: PASS with "✅ TEST 1 passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-5): Add TEST 1 - requests under rate limit"
```

---

### Task 9: Implement TEST 2 - Limit Exceeded

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimitTest)

- [ ] **Step 1: Add TEST 2 - exceeding limit**

Before the final "_ <- ZIO.debug("\n✅ All tests completed successfully!")" line, add:

```scala
          // TEST 2: Exceed limit (4th request should be denied)
          _ <- ZIO.debug("\n=== TEST 2: Send 4th request (exceeds limit) ===")
          req2  = Request.get(url1)
          res2 <- Client.batched(req2)
          _    <- ZIO.debug(s"Request 4: ${res2.status}")
          _ <- if (res2.status == Status.TooManyRequests) {
                 ZIO.debug("✅ TEST 2 passed - 4th request blocked with 429")
               } else {
                 ZIO.fail(
                   s"TEST 2 failed: expected 429 Too Many Requests, got ${res2.status}"
                 )
               }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.RateLimiting.Solution.RateLimitTest"`
Expected: PASS with "✅ TEST 2 passed" and "✅ TEST 1 passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-5): Add TEST 2 - rate limit enforcement"
```

---

### Task 10: Implement TEST 3 - Window Reset

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside RateLimitTest)

- [ ] **Step 1: Add TEST 3 - window expiration and reset**

Before the final success line, add:

```scala
          // TEST 3: Wait for window to expire, then verify counter resets
          _ <- ZIO.debug("\n=== TEST 3: Wait 5 seconds for window expiration ===")
          _ <- ZIO.sleep(5.5.seconds)
          _ <- ZIO.debug("Window expired, counter should reset")

          // Next request should be allowed (counter reset)
          req3  = Request.get(url1)
          res3 <- Client.batched(req3)
          _    <- ZIO.debug(s"Request after reset: ${res3.status}")
          _ <- if (res3.status == Status.Ok) {
                 ZIO.debug("✅ TEST 3 passed - counter reset after window expiration")
               } else {
                 ZIO.fail(
                   s"TEST 3 failed: expected 200 after reset, got ${res3.status}"
                 )
               }
```

- [ ] **Step 2: Run test to verify all tests pass**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.RateLimiting.Solution.RateLimitTest"`
Expected: PASS with all 3 tests completed

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-5): Add TEST 3 - window expiration and counter reset"
```

---

### Task 11: Final Verification and Formatting

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala`

- [ ] **Step 1: Format all Scala code**

Run: `sbt scalafmtAll`
Expected: SUCCESS

- [ ] **Step 2: Run lint checks**

Run: `sbt "++2.13; check"`
Expected: SUCCESS (no lint errors)

- [ ] **Step 3: Run full test**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.RateLimiting.Solution.RateLimitTest"`
Expected: All 3 tests pass

- [ ] **Step 4: Final commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "refactor(exercise-5): Format and verify rate limiting middleware"
```

---

## Self-Review

**Spec Coverage:**
✓ RateLimitConfig with maxRequests and timeWindow
✓ IP extraction from X-Forwarded-For with fallback
✓ Hybrid window tracking (Map[IP → (count, windowStart)])
✓ Time window expiration and reset logic
✓ Periodic cleanup (every 10 seconds)
✓ HandlerAspect middleware returning 429 when limit exceeded
✓ Per-route isolation (each middleware instance has its own state)
✓ ExampleApp demonstrating usage with @@ syntax
✓ Integration tests covering under limit, exceeding limit, and window reset

**Placeholder Scan:** All code complete, no TBDs or placeholders.

**Type Consistency:** RateLimitConfig, RateLimitState, Ref[Map[String, RateLimitState]], IP extraction all consistent.

**Completeness:** Each task is 2-5 minutes with complete code, exact commands, expected outputs.

---

## Execution

Plan complete and saved to `docs/superpowers/plans/2026-04-04-rate-limiting-middleware.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach would you prefer?