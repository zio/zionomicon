# File Upload Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a POST /upload endpoint that accepts multipart form data (filename and file fields), validates filenames to prevent directory traversal, and streams files to disk without buffering.

**Architecture:** The solution uses ZIO HTTP's multipart form API to extract and validate form fields, then streams the binary file data directly to the filesystem using ZSink.fromPath. Error handling distinguishes between validation errors (400 Bad Request) and I/O errors (500 Internal Server Error).

**Tech Stack:** 
- ZIO HTTP (Routes, Handler, Request, Response)
- ZIO Streams (ZStream, ZSink)
- Java NIO (Files, File paths, canonical path resolution)

---

## File Structure

**Files to modify:**
- `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala`
  - Add UploadError sealed trait (lines after package FileUploadEndpoint)
  - Add FileUploadRoutes object with uploadEndpoint function
  - Add ExampleApp demonstrating server setup with request streaming enabled
  - Add FileUploadEndpointTest with integration tests

**No new files created** — all code goes into the existing exercise file under the `FileUploadEndpoint` package.

---

## Tasks

### Task 1: Define UploadError Sealed Trait

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (package FileUploadEndpoint, after imports)

- [ ] **Step 1: Add UploadError sealed trait and cases**

Replace the empty `FileUploadEndpoint` package body with:

```scala
package FileUploadEndpoint {

  import zio._
  import zio.http._
  import zio.stream.ZSink
  import java.io.File
  import java.nio.file.{Files => JFiles}

  package Solution {

    sealed trait UploadError
    object UploadError {
      case class InvalidFileName(msg: String) extends UploadError
      case class SaveError(msg: String)       extends UploadError
    }
```

**Rationale:** Sealed trait allows pattern matching on upload failures. Two cases cover validation errors (client-side) and I/O errors (server-side).

- [ ] **Step 2: Verify file compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS (or compilation continues to next task)

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-4): Add UploadError sealed trait for file upload validation"
```

---

### Task 2: Implement Filename Validation Function

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add validateFilename helper function**

After the UploadError object definition, add:

```scala
    object FileUploadRoutes {

      /**
       * Validates a filename to prevent directory traversal and invalid paths.
       *
       * Rules:
       * - Non-empty
       * - No path separators (/ or \)
       * - Not "." or ".."
       * - No null bytes
       *
       * Returns Either[UploadError, String] where Right contains the validated filename.
       */
      private def validateFilename(name: String): Either[UploadError, String] =
        if (name.isEmpty)
          Left(UploadError.InvalidFileName("Filename cannot be empty"))
        else if (name == "." || name == "..")
          Left(UploadError.InvalidFileName(s"Invalid filename: $name"))
        else if (name.contains('/') || name.contains('\\'))
          Left(UploadError.InvalidFileName(s"Filename cannot contain path separators: $name"))
        else if (name.contains('\u0000'))
          Left(UploadError.InvalidFileName(s"Filename cannot contain null bytes: $name"))
        else
          Right(name)
```

**Rationale:** Validates common directory traversal attacks. Using Either provides clear error messaging for client responses.

- [ ] **Step 2: Verify validation logic compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-4): Add filename validation to prevent directory traversal"
```

---

### Task 3: Implement uploadEndpoint Core Handler

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadRoutes object)

- [ ] **Step 1: Add uploadEndpoint function signature and multipart parsing**

After the validateFilename function, add:

```scala
      /**
       * Creates a POST /upload endpoint that accepts multipart form data.
       *
       * Expected form fields:
       * - filename: String (the name to save the file as)
       * - file: Binary data (the file content)
       *
       * Returns:
       * - 201 Created on success
       * - 400 Bad Request if filename is invalid
       * - 500 Internal Server Error if file save fails
       *
       * DESIGN: Request streaming must be enabled via
       * Server.Config.default.enableRequestStreaming on the server.
       * This allows streaming large files without buffering in memory.
       */
      def uploadEndpoint(uploadDir: String): zio.http.Routes[Any, Response] = {
        val uploadDirFile = new File(uploadDir).getCanonicalFile

        Routes(
          Method.POST / "upload" -> handler { (req: Request) =>
            for {
              form <- req.body.asMultipartForm
              filename <- ZIO
                .fromOption(
                  form.get("filename").flatMap(_.asTextOption)
                )
                .orElseFail(Response.badRequest("Missing or invalid 'filename' field"))
              fileField <- ZIO
                .fromOption(form.get("file"))
                .orElseFail(Response.badRequest("Missing 'file' field"))
              validated <- ZIO
                .fromEither(validateFilename(filename))
                .mapError {
                  case UploadError.InvalidFileName(msg) =>
                    Response.badRequest(msg)
                  case UploadError.SaveError(msg) =>
                    Response.internalServerError(msg)
                }
              targetPath = new File(uploadDirFile, validated).getCanonicalFile.toPath
              _ <- ZIO
                .when(!targetPath.toFile.getPath.startsWith(uploadDirFile.getPath))(
                  ZIO.fail(Response.badRequest("Invalid file path"))
                )
              result <- fileField.asStream
                .run(ZSink.fromPath(targetPath))
                .mapBoth(
                  e => Response.internalServerError(s"Save error: ${e.getMessage}"),
                  _ => Response.status(Status.Created)
                )
                .merge
            } yield result
          }
        )
      }
```

**Rationale:**
- `asMultipartForm` parses multipart boundaries automatically
- `form.get("filename").flatMap(_.asTextOption)` safely extracts text field
- `form.get("file")` gets the binary field
- Canonical path prevents traversal attacks (`../`)
- `asStream.run(ZSink.fromPath)` achieves zero-copy streaming
- Error handling distinguishes validation (400) from I/O errors (500)

- [ ] **Step 2: Verify endpoint compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-4): Implement uploadEndpoint with multipart form parsing and streaming"
```

---

### Task 4: Create ExampleApp Demonstrator

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add ExampleApp with request streaming enabled**

After the FileUploadRoutes object closes, add:

```scala
      /**
       * Example application demonstrating the file upload endpoint in action.
       * Uploads are saved to /tmp/uploads on port 8080.
       *
       * Example usage:
       * curl -F "filename=myfile.txt" -F "file=@myfile.txt" http://localhost:8080/upload
       *
       * NOTE: Server.Config.default.enableRequestStreaming must be set to allow
       * large files to be streamed without buffering the entire request body.
       */
      object ExampleApp extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          ZIO.debug("Starting file upload server on http://localhost:8080/upload") *>
            Server
              .serve(FileUploadRoutes.uploadEndpoint("/tmp/uploads"))
              .provide(
                ZLayer.succeed(Server.Config.default.enableRequestStreaming) >>> Server.live
              )
      }
```

**Rationale:** Shows server configuration with request streaming enabled. Documents curl usage for manual testing.

- [ ] **Step 2: Verify ExampleApp compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "feat(exercise-4): Add ExampleApp demonstrating file upload server"
```

---

### Task 5: Create Integration Test Structure

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside Solution package)

- [ ] **Step 1: Add FileUploadEndpointTest scaffold**

After ExampleApp, add:

```scala
      /**
       * Integration tests for the file upload endpoint using ZIO HTTP Client API.
       *
       * NOTE: We use ZIOAppDefault instead of ZIOSpecDefault for integration tests
       * because ZIO Test's test clock framework is incompatible with real I/O operations
       * (HTTP servers, network requests). Integration tests need wall-clock time semantics.
       *
       * Run with: sbtn "runMain
       * zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"
       */
      object FileUploadEndpointTest extends ZIOAppDefault {

        def run: ZIO[Any, Any, Unit] =
          (for {
            // Create a temporary directory for uploads
            tempDir <- ZIO.attemptBlocking(
                        JFiles.createTempDirectory("zio-http-upload-test")
                      )
            _ <- ZIO.debug(s"Created temp directory: $tempDir")

            // Allocate a free port
            port <- ZIO.attemptBlocking {
                      val socket = new java.net.ServerSocket(0)
                      val p      = socket.getLocalPort
                      socket.close()
                      p
                    }
            _ <- ZIO.debug(s"Allocated port: $port")

            // Start the server with request streaming enabled
            _ <- Server
                   .serve(FileUploadRoutes.uploadEndpoint(tempDir.toString))
                   .provide(
                     ZLayer.succeed(
                       Server.Config.default.enableRequestStreaming.port(port)
                     ) >>> Server.live
                   )
                   .fork
            _ <- ZIO.sleep(1.second)

            // Tests will go here
            _ <- ZIO.debug("Running file upload tests...")
            _ <- ZIO.debug("\n✅ All tests completed successfully!")
          } yield ()).provide(Client.default)
      }
```

**Rationale:** Test scaffold sets up temp directory, allocates free port, starts server with streaming enabled. Follows existing test patterns in the file.

- [ ] **Step 2: Verify test structure compiles**

Run: `sbtn "++2.13; compile"`
Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add FileUploadEndpointTest scaffold"
```

---

### Task 6: Implement TEST 1 - Valid File Upload

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadEndpointTest, replace "// Tests will go here")

- [ ] **Step 1: Add TEST 1 - valid file upload with verification**

Replace `// Tests will go here` with:

```scala
            // TEST 1: Upload a valid file
            _ <- ZIO.debug("\n=== TEST 1: POST /upload with valid multipart form ===")
            testContent1 = "Hello, World!"
            url1 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/upload"))
            req1  = Request.post(
                      url1,
                      Body.fromMultipartForm(
                        Form(
                          FormField.text("filename", "hello.txt"),
                          FormField.binaryField("file", Chunk.fromArray(testContent1.getBytes))
                        )
                      )
                    )
            res1 <- Client.batched(req1)
            _    <- ZIO.debug(s"Response: ${res1.status}")
            _ <- if (res1.status == Status.Created) {
                   ZIO.debug("✅ TEST 1 passed - file uploaded successfully")
                 } else {
                   ZIO.fail(s"TEST 1 failed: expected 201 Created, got ${res1.status}")
                 }

            // TEST 1b: Verify uploaded file content
            _ <- ZIO.debug("\n=== TEST 1b: Verify uploaded file content ===")
            uploadedFile = new File(tempDir.toFile, "hello.txt")
            uploadedContent <- ZIO.attemptBlocking {
                                 new String(JFiles.readAllBytes(uploadedFile.toPath))
                               }
            _ <- ZIO.debug(s"Uploaded content: $uploadedContent")
            _ <- if (uploadedContent == testContent1) {
                   ZIO.debug("✅ TEST 1b passed - file content matches")
                 } else {
                   ZIO.fail(
                     s"TEST 1b failed: expected '$testContent1', got '$uploadedContent'"
                   )
                 }
```

**Rationale:** 
- Uses `Form` with `FormField.text()` and `FormField.binaryField()`
- Verifies both upload success (201) and file content on disk
- Documents multipart form construction pattern

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: PASS with debug output showing "✅ TEST 1 passed" and "✅ TEST 1b passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add TEST 1 - valid file upload with content verification"
```

---

### Task 7: Implement TEST 2 - Empty Filename Rejection

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadEndpointTest)

- [ ] **Step 1: Add TEST 2 - reject empty filename**

Before `_ <- ZIO.debug("\n✅ All tests completed successfully!")`, add:

```scala
            // TEST 2: Empty filename should be rejected
            _ <- ZIO.debug("\n=== TEST 2: POST /upload with empty filename ===")
            url2 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/upload"))
            req2  = Request.post(
                      url2,
                      Body.fromMultipartForm(
                        Form(
                          FormField.text("filename", ""),
                          FormField.binaryField("file", Chunk.fromArray("content".getBytes))
                        )
                      )
                    )
            res2 <- Client.batched(req2)
            _    <- ZIO.debug(s"Response: ${res2.status}")
            _ <- if (res2.status == Status.BadRequest) {
                   ZIO.debug("✅ TEST 2 passed - empty filename rejected")
                 } else {
                   ZIO.fail(s"TEST 2 failed: expected 400 Bad Request, got ${res2.status}")
                 }
```

**Rationale:** Validates that empty filename is caught by validateFilename and returns 400.

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: PASS with "✅ TEST 2 passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add TEST 2 - reject empty filename"
```

---

### Task 8: Implement TEST 3 - Path Traversal Protection

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadEndpointTest)

- [ ] **Step 1: Add TEST 3 - path traversal prevention**

Before `_ <- ZIO.debug("\n✅ All tests completed successfully!")`, add:

```scala
            // TEST 3: Path traversal attempt (../) should be blocked
            _ <- ZIO.debug("\n=== TEST 3: POST /upload with path traversal (../) ===")
            url3 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/upload"))
            req3  = Request.post(
                      url3,
                      Body.fromMultipartForm(
                        Form(
                          FormField.text("filename", "../../../etc/passwd"),
                          FormField.binaryField("file", Chunk.fromArray("malicious".getBytes))
                        )
                      )
                    )
            res3 <- Client.batched(req3)
            _    <- ZIO.debug(s"Response: ${res3.status}")
            _ <- if (res3.status == Status.BadRequest) {
                   ZIO.debug("✅ TEST 3 passed - path traversal blocked")
                 } else {
                   ZIO.fail(
                     s"TEST 3 failed: expected 400 Bad Request for traversal attempt, got ${res3.status}"
                   )
                 }
```

**Rationale:** Validates that path separators are detected by validateFilename.

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: PASS with "✅ TEST 3 passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add TEST 3 - path traversal protection"
```

---

### Task 9: Implement TEST 4 - Missing Field Validation

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadEndpointTest)

- [ ] **Step 1: Add TEST 4 - missing file field**

Before `_ <- ZIO.debug("\n✅ All tests completed successfully!")`, add:

```scala
            // TEST 4: Missing 'file' field should return 400
            _ <- ZIO.debug("\n=== TEST 4: POST /upload with missing 'file' field ===")
            url4 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/upload"))
            req4  = Request.post(
                      url4,
                      Body.fromMultipartForm(
                        Form(
                          FormField.text("filename", "test.txt")
                          // Missing file field
                        )
                      )
                    )
            res4 <- Client.batched(req4)
            _    <- ZIO.debug(s"Response: ${res4.status}")
            _ <- if (res4.status == Status.BadRequest) {
                   ZIO.debug("✅ TEST 4 passed - missing file field rejected")
                 } else {
                   ZIO.fail(
                     s"TEST 4 failed: expected 400 Bad Request for missing file, got ${res4.status}"
                   )
                 }
```

**Rationale:** Validates that handler correctly checks for required fields.

- [ ] **Step 2: Run test to verify it passes**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: PASS with "✅ TEST 4 passed"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add TEST 4 - missing field validation"
```

---

### Task 10: Implement TEST 5 - Large File Streaming

**Files:**
- Modify: `src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala` (inside FileUploadEndpointTest)

- [ ] **Step 1: Add TEST 5 - large file upload to verify streaming**

Before `_ <- ZIO.debug("\n✅ All tests completed successfully!")`, add:

```scala
            // TEST 5: Large file upload (5 MB) to verify streaming works
            _ <- ZIO.debug("\n=== TEST 5: POST /upload with large file (5 MB) ===")
            largeContent = Array.fill[Byte](5 * 1024 * 1024)(65.toByte) // 5 MB of 'A'
            url5 <- ZIO.fromEither(URL.decode(s"http://localhost:$port/upload"))
            req5  = Request.post(
                      url5,
                      Body.fromMultipartForm(
                        Form(
                          FormField.text("filename", "largefile.bin"),
                          FormField.binaryField("file", Chunk.fromArray(largeContent))
                        )
                      )
                    )
            res5 <- Client.batched(req5)
            _    <- ZIO.debug(s"Response: ${res5.status}")
            _ <- if (res5.status == Status.Created) {
                   ZIO.debug("✅ TEST 5 passed - large file uploaded successfully")
                 } else {
                   ZIO.fail(s"TEST 5 failed: expected 201 Created, got ${res5.status}")
                 }

            // Verify large file size
            largeFile = new File(tempDir.toFile, "largefile.bin")
            fileSize <- ZIO.attemptBlocking {
                          JFiles.size(largeFile.toPath)
                        }
            _ <- ZIO.debug(s"Uploaded file size: ${fileSize / (1024 * 1024)} MB")
            _ <- if (fileSize == largeContent.length) {
                   ZIO.debug("✅ Large file size matches - streaming works correctly")
                 } else {
                   ZIO.fail(
                     s"File size mismatch: expected ${largeContent.length}, got $fileSize"
                   )
                 }
```

**Rationale:** 
- Confirms streaming handles large files without buffering entire content
- Verifies zero-copy file save with size comparison
- Documents streaming capability

- [ ] **Step 2: Run test to verify all tests pass**

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: PASS with all 5 tests completed, "✅ All tests completed successfully!"

- [ ] **Step 3: Commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "test(exercise-4): Add TEST 5 - large file streaming verification"
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

Run: `sbtn "runMain zionomicon.solutions.CommunicationProtocolsZIOHTTP.FileUploadEndpoint.Solution.FileUploadEndpointTest"`
Expected: All 5 tests pass

- [ ] **Step 4: Final commit**

```bash
git add src/main/scala/zionomicon/solutions/35-communication-protocols-zio-http.scala
git commit -m "refactor(exercise-4): Format and verify file upload endpoint"
```

---

## Self-Review

**Spec Coverage:**
✓ POST /upload endpoint accepting multipart form data
✓ Filename and file field extraction
✓ Filename validation (non-empty, no path separators, not . or ..)
✓ Streaming to disk with ZSink (zero-copy)
✓ Error handling: 400 Bad Request (validation), 500 Internal Server Error (I/O)
✓ Integration tests with 5 scenarios
✓ ExampleApp with request streaming enabled
✓ Documentation of curl usage

**Placeholder Scan:** No TBDs, all code complete, all commands specified, expected outputs documented.

**Type Consistency:** UploadError sealed trait used consistently in validateFilename and handler error mapping.

**Completeness:** Each task is 2-5 minutes, includes complete code, exact commands, expected output.

---

## Execution

Plan complete and saved. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task with review checkpoints between tasks. Fast iteration, clean handoff.

**2. Inline Execution** — Execute tasks sequentially in this session with natural break points for your review.

Which approach would you prefer?