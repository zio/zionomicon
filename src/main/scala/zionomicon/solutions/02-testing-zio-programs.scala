package zionomicon.solutions

package TestingZIOPrograms {

  import zio._
  import zio.test._

  import scala.annotation.tailrec

  /**
   *   1. Write a ZIO program that simulates a countdown timer (e.g., prints
   *      numbers from 5 to 1, with a 1-second delay between each). Test this
   *      program using TestClock.
   */
  object CountdownTimer extends ZIOSpecDefault {
    def countdown(n: Int): ZIO[Any, Nothing, Unit] =
      if (n <= 0) ZIO.unit
      else
        for {
          _ <- Console.printLine(s"Countdown: $n").orDie
          _ <- ZIO.sleep(1.second)
          _ <- countdown(n - 1)
        } yield ()

    override def spec =
      suite("Countdown Timer Spec")(
        test("should count down from 5 to 1") {
          for {
            f <- countdown(5).fork
            _ <- TestClock.adjust(
                   5.seconds
                 ) // Adjust the clock to simulate time passing
            _ <- f.join
            o <- TestConsole.output
          } yield assertTrue(
            o == Vector(
              "Countdown: 5\n",
              "Countdown: 4\n",
              "Countdown: 3\n",
              "Countdown: 2\n",
              "Countdown: 1\n"
            )
          )
        }
      )
  }

  /**
   *   2. Create a simple cache that expires entries after a certain duration.
   *      Implement a program that adds items to the cache and tries to retrieve
   *      them. Write tests using `TestClock` to verify that items are available
   *      before expiration and unavailable after expiration.
   */
  object CacheWithExpiration extends ZIOSpecDefault {

    import java.util.concurrent.TimeUnit

    case class CacheEntry[V](value: V, expirationTime: Long)

    case class Cache[K, V](
      private val storage: Ref[Map[K, CacheEntry[V]]],
      expiration: Long
    ) {

      def put(key: K, value: V): ZIO[Any, Nothing, Unit] =
        for {
          currentTime   <- Clock.currentTime(TimeUnit.MILLISECONDS)
          expirationTime = currentTime + expiration
          _ <-
            storage.update(_.updated(key, CacheEntry(value, expirationTime)))
        } yield ()

      def get(key: K): ZIO[Any, Nothing, Option[V]] =
        for {
          currentTime <- Clock.currentTime(TimeUnit.MILLISECONDS)
          storageMap  <- storage.get
          result = storageMap.get(key) match {
                     case Some(entry) if entry.expirationTime > currentTime =>
                       Some(entry.value)
                     case _ =>
                       None
                   }
          // Optionally clean up expired entries
          _ <- storage.update(_.filter { case (_, entry) =>
                 entry.expirationTime > currentTime
               })
        } yield result
    }

    object Cache {
      def make[K, V](expiration: Long): UIO[Cache[K, V]] =
        for {
          ref <- Ref.make(Map.empty[K, CacheEntry[V]])
        } yield Cache(ref, expiration)
    }

    override def spec =
      suite("Cache With Expiration Spec")(
        test("should store and retrieve items before expiration") {
          for {
            cache <- Cache.make[String, String](5000) // 5 seconds expiration
            _     <- cache.put("key1", "value1")
            value <- cache.get("key1")
          } yield assertTrue(value.contains("value1"))
        },
        test("should not retrieve items after expiration") {
          for {
            cache <- Cache.make[String, String](1000) // 1 second expiration
            _     <- cache.put("key2", "value2")
            _ <- TestClock.adjust(
                   1500.millis
                 ) // Adjust the clock to simulate time passing
            value <- cache.get("key2")
          } yield assertTrue(value.isEmpty)
        },
        test("should handle multiple items with different expiration times") {
          for {
            cache <- Cache.make[String, String](2000) // 2 seconds expiration
            _     <- cache.put("key1", "value1")
            _     <- TestClock.adjust(1000.millis)    // 1 second passed
            _ <- cache.put(
                   "key2",
                   "value2"
                 ) // This will expire 1 second after key1
            _      <- TestClock.adjust(1500.millis) // Total 2.5 seconds passed
            value1 <- cache.get("key1")             // Should be expired
            value2 <- cache.get("key2")             // Should still be valid
          } yield assertTrue(value1.isEmpty && value2.contains("value2"))
        },
        test("should overwrite existing keys with new expiration time") {
          for {
            cache <- Cache.make[String, String](1000) // 1 second expiration
            _     <- cache.put("key1", "value1")
            _     <- TestClock.adjust(900.millis)     // Almost expired
            _     <- cache.put("key1", "updated")     // Reset expiration
            _ <-
              TestClock.adjust(500.millis) // Total 1.4 seconds from first put
            value <- cache.get("key1")
          } yield assertTrue(value.contains("updated"))
        }
      )

  }

  /**
   *   3. Create a rate limiter that allows a maximum of N operations per
   *      minute. Implement a program that uses this rate limiter. Write tests
   *      using `TestClock` to verify that the rate limiter correctly allows or
   *      blocks operations based on the time window.
   */
  object RateLimiterSpec extends ZIOSpecDefault {

    import zio.test.Assertion._

    import java.time.Instant

    /**
     * Rate limiter that allows a maximum of N operations per minute. Uses a
     * sliding window approach to track operations.
     */
    trait RateLimiter {
      def tryAcquire: UIO[Boolean]
    }

    object RateLimiter {

      /**
       * Creates a rate limiter that allows maxOps operations per minute
       */
      def make(maxOps: Int): UIO[RateLimiter] =
        for {
          // Store timestamps of operations within the current window
          operationTimestamps <- Ref.make[List[Instant]](List.empty)
        } yield new RateLimiter {

          def tryAcquire: UIO[Boolean] =
            for {
              now         <- Clock.instant
              oneMinuteAgo = now.minusSeconds(60)

              acquired <- operationTimestamps.modify { timestamps =>
                            // Remove timestamps older than 1 minute
                            val validTimestamps =
                              timestamps.filter(_.isAfter(oneMinuteAgo))

                            // Check if we can add a new operation
                            if (validTimestamps.size < maxOps) {
                              // Add current timestamp and allow operation
                              (true, now :: validTimestamps)
                            } else {
                              // Rate limit exceeded
                              (false, validTimestamps)
                            }
                          }
            } yield acquired
        }
    }

    override def spec =
      suite("Rate Limiter Spec")(
        test("should allow operations within rate limit") {
          for {
            rateLimiter <- RateLimiter.make(5) // Allow 5 ops per minute

            // Try 5 operations - all should be allowed
            results <- ZIO.foreach(1 to 5)(_ => rateLimiter.tryAcquire)

          } yield assert(results)(forall(equalTo(true)))
        },
        test("should block operations exceeding rate limit") {
          for {
            rateLimiter <- RateLimiter.make(3) // Allow 3 ops per minute

            // First 3 operations should be allowed
            firstBatch <- ZIO.foreach(1 to 3)(_ => rateLimiter.tryAcquire)

            // Next 2 operations should be blocked
            secondBatch <- ZIO.foreach(1 to 2)(_ => rateLimiter.tryAcquire)

          } yield {
            assert(firstBatch)(forall(equalTo(true))) &&
            assert(secondBatch)(forall(equalTo(false)))
          }
        },
        test("should reset after time window passes") {
          for {
            rateLimiter <- RateLimiter.make(2) // Allow 2 ops per minute

            // Use up the limit
            _ <- rateLimiter.tryAcquire
            _ <- rateLimiter.tryAcquire

            // This should be blocked
            blockedResult <- rateLimiter.tryAcquire

            // Advance time by 61 seconds
            _ <- TestClock.adjust(61.seconds)

            // Now operations should be allowed again
            allowedResult1 <- rateLimiter.tryAcquire
            allowedResult2 <- rateLimiter.tryAcquire

          } yield {
            assert(blockedResult)(equalTo(false)) &&
            assert(allowedResult1)(equalTo(true)) &&
            assert(allowedResult2)(equalTo(true))
          }
        },
        test("should use sliding window for rate limiting") {
          for {
            rateLimiter <- RateLimiter.make(3) // Allow 3 ops per minute

            // Perform operations at different times
            op1 <- rateLimiter.tryAcquire // t=0
            _   <- TestClock.adjust(20.seconds)

            op2 <- rateLimiter.tryAcquire // t=20
            _   <- TestClock.adjust(20.seconds)

            op3 <- rateLimiter.tryAcquire // t=40

            // Should be blocked (3 ops in last 60 seconds)
            op4 <- rateLimiter.tryAcquire // t=40

            _ <- TestClock.adjust(21.seconds) // t=61

            // First operation is now outside the window, so this should be allowed
            op5 <- rateLimiter.tryAcquire // t=61

          } yield {
            assert(op1)(equalTo(true)) &&
            assert(op2)(equalTo(true)) &&
            assert(op3)(equalTo(true)) &&
            assert(op4)(equalTo(false)) &&
            assert(op5)(equalTo(true))
          }
        },
        test("should handle burst of operations correctly") {
          for {
            rateLimiter <- RateLimiter.make(5) // Allow 5 ops per minute

            // Burst of 10 operations at once
            results <- ZIO.foreach(1 to 10)(_ => rateLimiter.tryAcquire)

            allowed = results.take(5)
            blocked = results.drop(5)

          } yield {
            assert(allowed)(forall(equalTo(true))) &&
            assert(blocked)(forall(equalTo(false)))
          }
        }
      )
  }

  /**
   *   4. Implement a function that reverses a list, then write a property-based
   *      test to verify that reversing a list twice returns the original list.
   */
  object ReverseListSpec extends ZIOSpecDefault {
    def reverseList[A](list: List[A]): List[A] = {
      @annotation.tailrec
      def loop(remaining: List[A], reversed: List[A]): List[A] =
        remaining match {
          case Nil          => reversed
          case head :: tail => loop(tail, head :: reversed)
        }

      loop(list, Nil)
    }

    override def spec =
      suite("Reverse List Spec")(
        test("reversing a list twice returns the original list") {
          check(Gen.listOf(Gen.int)) { list =>
            assertTrue(reverseList(reverseList(list)) == list)
          }
        }
      )
  }

  /**
   *   5. Implement an AVL tree (self-balancing binary search tree) with insert
   *      and delete operations. Write property-based tests to verify that the
   *      tree remains balanced after each operation. A balanced tree is one
   *      where the height of every node's left and right subtrees differs by at
   *      most one.
   */
  object AVLTreeSpec extends ZIOSpecDefault {

    import zio.test.Gen

    sealed trait AVLNode[+A] {
      def height: Int

      def balanceFactor: Int

      def toList: List[A]

      def isBalanced: Boolean

      def contains[B >: A](value: B)(implicit ord: Ordering[B]): Boolean

      def min: Option[A]

      def max: Option[A]
    }

    case object Empty extends AVLNode[Nothing] {
      val height        = 0
      val balanceFactor = 0
      val toList        = List.empty
      val isBalanced    = true

      def contains[B](value: B)(implicit ord: Ordering[B]) = false

      val min = None
      val max = None
    }

    case class Node[A](
      value: A,
      left: AVLNode[A],
      right: AVLNode[A],
      height: Int
    ) extends AVLNode[A] {

      def balanceFactor: Int = left.height - right.height

      def contains[B >: A](needle: B)(implicit ord: Ordering[B]): Boolean = {
        import ord._
        if (needle < value) left.contains(needle)
        else if (needle > value) right.contains(needle)
        else true
      }

      def toList: List[A] = left.toList ++ List(value) ++ right.toList

      def isBalanced: Boolean = {
        val bf = balanceFactor
        bf >= -1 && bf <= 1 && left.isBalanced && right.isBalanced
      }

      @tailrec
      final def min: Option[A] = left match {
        case Empty         => Some(value)
        case node: Node[A] => node.min
      }

      @tailrec
      final def max: Option[A] = right match {
        case Empty         => Some(value)
        case node: Node[A] => node.max
      }
    }

    object Node {
      def apply[A](value: A, left: AVLNode[A], right: AVLNode[A]): Node[A] = {
        val h = 1 + math.max(left.height, right.height)
        new Node(value, left, right, h)
      }
    }

    // AVL Tree operations
    object AVLTree {

      def empty[A]: AVLNode[A] = Empty

      private def rotateLeft[A](node: Node[A]): Node[A] = node.right match {
        case Node(rv, rl, rr, _) =>
          Node(rv, Node(node.value, node.left, rl), rr)
        case Empty => node // Should not happen in a valid AVL tree
      }

      private def rotateRight[A](node: Node[A]): Node[A] = node.left match {
        case Node(lv, ll, lr, _) =>
          Node(lv, ll, Node(node.value, lr, node.right))
        case Empty => node // Should not happen in a valid AVL tree
      }

      private def balance[A](node: Node[A]): AVLNode[A] = {
        val bf = node.balanceFactor

        if (bf > 1) {
          // Left-heavy
          node.left match {
            case ln: Node[A] if ln.balanceFactor < 0 =>
              // Left-Right case
              rotateRight(Node(node.value, rotateLeft(ln), node.right))
            case _: Node[A] =>
              // Left-Left case
              rotateRight(node)
            case Empty => node
          }
        } else if (bf < -1) {
          // Right-heavy
          node.right match {
            case rn: Node[A] if rn.balanceFactor > 0 =>
              // Right-Left case
              rotateLeft(Node(node.value, node.left, rotateRight(rn)))
            case _: Node[A] =>
              // Right-Right case
              rotateLeft(node)
            case Empty => node
          }
        } else node
      }

      def insert[A](tree: AVLNode[A], value: A)(implicit
        ord: Ordering[A]
      ): AVLNode[A] = {
        import ord._
        tree match {
          case Empty => Node(value, Empty, Empty)
          case node @ Node(v, l, r, _) =>
            if (value < v) {
              balance(Node(v, insert(l, value), r))
            } else if (value > v) {
              balance(Node(v, l, insert(r, value)))
            } else node // Value already exists
        }
      }

      def delete[A](tree: AVLNode[A], value: A)(implicit
        ord: Ordering[A]
      ): AVLNode[A] = {
        import ord._
        tree match {
          case Empty => Empty
          case node @ Node(v, l, r, _) =>
            if (value < v) {
              balance(Node(v, delete(l, value), r))
            } else if (value > v) {
              balance(Node(v, l, delete(r, value)))
            } else {
              // Found the node to delete
              (l, r) match {
                case (Empty, Empty)                  => Empty
                case (Empty, right)                  => right
                case (left, Empty)                   => left
                case (left: Node[A], right: Node[A]) =>
                  // Find the inorder successor (minimum in right subtree)
                  right.min match {
                    case Some(successor) =>
                      balance(Node(successor, left, delete(right, successor)))
                    case None => left // Should not happen
                  }
              }
            }
        }
      }

      def size[A](tree: AVLNode[A]): Int = tree match {
        case Empty            => 0
        case Node(_, l, r, _) => 1 + size(l) + size(r)
      }

      def isBST[A: Ordering](tree: AVLNode[A]): Boolean = {
        def check(node: AVLNode[A], min: Option[A], max: Option[A]): Boolean =
          node match {
            case Empty => true
            case Node(v, l, r, _) =>
              val ord      = implicitly[Ordering[A]]
              val validMin = min.forall(ord.lt(_, v))
              val validMax = max.forall(ord.gt(_, v))
              validMin && validMax &&
              check(l, min, Some(v)) &&
              check(r, Some(v), max)
          }

        check(tree, None, None)
      }
    }

    override def spec =
      suite("AVL Tree Spec")(
        suite("Balance Properties")(
          test("should maintain balance after insertions") {
            check(Gen.listOf(Gen.int(-1000, 1000))) { list =>
              val tree = list.foldLeft(AVLTree.empty[Int])(AVLTree.insert)
              assertTrue(
                tree.isBalanced,
                AVLTree.isBST(tree),
                tree.toList.sorted == list.distinct.sorted
              )
            }
          },
          test("should maintain balance after deletions") {
            check(
              Gen.listOf(Gen.int(-100, 100)),
              Gen.listOf(Gen.int(-100, 100))
            ) { (insertList, deleteList) =>
              val tree =
                insertList.foldLeft(AVLTree.empty[Int])(AVLTree.insert)
              val afterDeletions = deleteList.foldLeft(tree)(AVLTree.delete)

              assertTrue(
                afterDeletions.isBalanced,
                AVLTree.isBST(afterDeletions),
                afterDeletions.toList.sorted == insertList.distinct
                  .filterNot(deleteList.contains)
                  .sorted
              )
            }
          }
        )
      )
  }
}
