package zionomicon.exercises

import zio._
import Console._

object TheFiberModel_Solutions {
    
    /* 
        1. Write a ZIO program that forks two effects, one that prints “Hello” after a two-
        second delay and another that prints “World” after a one-second delay. Ensure both
        effects run concurrently.
    */
    object Question1 {

        val first_effect = ZIO.sleep(2.seconds) *> Console.printLine("Hello")
        val second_effect = ZIO.sleep(1.second) *> Console.printLine("World")

        def run = for {
            first <- first_effect.fork
            second <- second_effect.fork
            _ <- first.join
            _ <- second.join
        } yield ()

    }

    /* 
      Modify the previous program to print “Done” only after both forked effects have completed.
    */

    object Question2 {
        val first_effect  = ZIO.sleep(2.seconds) *> Console.printLine("Hello")
        val second_effect = ZIO.sleep(1.second) *> Console.printLine("World")

        def run = for {
            first <- first_effect.fork
            second <- second_effect.fork
            _ <- first.join
            _ <- second.join
            _ <- Console.printLine("Done")
        } yield ()
    }

    /* 
     Write a program that starts a long-running effect (e.g., printing numbers every second), then interrupts it after 5 seconds.
    */
    object Question3 {
        val printNumbers = ZIO.randomWith(_.nextInt).repeat(Schedule.fixed(1.second))

        def run = 
            for {
                fiber <- printNumbers.fork
                _ <- ZIO.sleep(5.seconds)
                _ <- fiber.interrupt
                _ <- Console.printLine("Done")
            } yield ()
    }

    /* 
        Create a program that forks an effect that might fail. Use await to handle both
        success and failure cases.
    */

    object Question4 {
        val faillibleEffect = (n: Int) => ZIO.attempt(n / (n - 1)).refineOrDie {
            case _: ArithmeticException => "Division by zero"
        }

        def run = for {
            fibers  <- ZIO.foreach((1 until 10).toList)(num => faillibleEffect(num).fork)
            results <- ZIO.foreach(fibers)(_.await)
            _ <- ZIO.foreach(results)(res => res.foldZIO(
                e => ZIO.debug("The fiber has failed with: " + e),
                s => ZIO.debug("The fiber has completed with: " + s)
            ))
        } yield ()
    }

    /* 
        Create a program with an uninterruptible section that simulates a critical operation.
        Try to interrupt it and observe the behavior.
     */

    object Question5 {

        def criticalOperation: ZIO[Any, Throwable, Unit] = 
            ZIO.attempt(while(true) { Thread.sleep (100)}).uninterruptible

        def run = for {
            fiber <- criticalOperation.fork
            _ <- fiber.interrupt
            _ <- ZIO.debug("Operation Finished!") // <- will never reach here and operation will never stop

        } yield ()

    }

    /* 
        6. Write a program demonstrating fiber supervision where a parent fiber forks two
          child fibers. Interrupt the parent and observe what happens to the children.
        
        Below is the response 

        Parent fiber beginning execution...
        Child fiber beginning execution...
        Child fiber 2 beginning execution...
 
        ---- Program complete ----

        "Hello from a parent fiber"
        "Hello from a child fiber"
        "Hello from a child fiber 2"

        will never reach 
    */

    object Question6 {

        val child = 
            (printLine("Child fiber 1 beginning execution...").orDie *> 
            ZIO.sleep(5.seconds) *> 
            printLine("Hello from a child fiber 1!").orDie).onInterrupt(ZIO.debug("Child fiber 1 is interrupted"))

        val child2 = 
            (printLine("Child fiber 2 beginning execution...").orDie *> 
            ZIO.sleep(5.seconds) *> 
            printLine("Hello from a child fiber 2!").orDie).onInterrupt(ZIO.debug("Child fiber 2 is interrupted"))

        val parent = 
            (printLine("Parent fiber beginning execution...").orDie *> 
            child.fork *> 
            child2.fork *>
            ZIO.sleep(3.seconds) *> 
            printLine("Hello from a parent fiber!").orDie).onInterrupt(ZIO.debug("Parent Fiber is interrupted"))
        
        def run =
            for {
                parentfiber <- parent.fork
                _ <- ZIO.sleep(1.second)
                _ <- parentfiber.interrupt
                _ <- ZIO.sleep(3.seconds)
                _ <- printLine("---- Program complete ----").orDie
            } yield ()

    }

    /* 
        7. Change one of the child fibers in the previous program to be a daemon fiber. Observe
           the difference in behavior when the parent is interrupted.
    */

    object Question7 {

        val child = 
            (printLine("Child fiber 1 beginning execution...").orDie *> 
            ZIO.sleep(5.seconds) *> 
            printLine("Hello from a child fiber 1!").orDie).onInterrupt(ZIO.debug("Child fiber 1 is interrupted"))

        val child2 = 
            (printLine("Child fiber 2 beginning execution...").orDie *> 
            ZIO.sleep(5.seconds) *> 
            printLine("Hello from a child fiber 2!").orDie).onInterrupt(ZIO.debug("Child fiber 2 is interrupted"))

        val parent = 
            (printLine("Parent fiber beginning execution...").orDie *> 
            child.fork *> 
            child2.forkDaemon *>
            ZIO.sleep(3.seconds) *> 
            printLine("Hello from a parent fiber!").orDie).onInterrupt(ZIO.debug("Parent Fiber is interrupted"))
                
        def run =
            for {
                fiber <- parent.fork
                _ <- ZIO.sleep(1.second)
                _ <- fiber.interrupt
                _ <- ZIO.sleep(3.seconds)
                _ <- printLine("---- Program complete ----").orDie
            } yield ()

    }

}