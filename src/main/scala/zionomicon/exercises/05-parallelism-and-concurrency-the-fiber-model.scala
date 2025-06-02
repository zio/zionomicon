package zionomicon.exercises

import zio._

object TheFiberModel {

  /*
        1. Write a ZIO program that forks two effects, one that prints “Hello” after a two-
        second delay and another that prints “World” after a one-second delay. Ensure both
        effects run concurrently.
   */
  object Question1 {}

  /*
      Modify the previous program to print “Done” only after both forked effects have completed.
   */

  object Question2 {}

  /*
     Write a program that starts a long-running effect (e.g., printing numbers every second), then interrupts it after 5 seconds.
   */
  object Question3 {}

  /*
        Create a program that forks an effect that might fail. Use await to handle both
        success and failure cases.
   */

  object Question4 {}

  /*
        Create a program with an uninterruptible section that simulates a critical operation.
        Try to interrupt it and observe the behavior.
   */

  object Question5 {}

  /*
        6. Write a program demonstrating fiber supervision where a parent fiber forks two
          child fibers. Interrupt the parent and observe what happens to the children.
        
   */

  object Question6 {}

  /*
        7. Change one of the child fibers in the previous program to be a daemon fiber. Observe
           the difference in behavior when the parent is interrupted.
   */

  object Question7 {}

}
