package zionomicon.solutions

package HubBroadcasting {

  /**
   *   1. Create a chatroom system using `Hub` where multiple users can join the
   *      chat so each message sent is broadcast to all users. Each message sent
   *      by a user is received by all other users. Users can leave the chat.
   *      There is also a process that logs all messages sent to the chat room
   *      in a file:
   *
   * {{{
   * trait UserSession {
   *   def sendMessage(message: String): UIO[Unit]
   *   def receiveMessages: ZIO[Scope, Nothing, Dequeue[ChatEvent]]
   *   def leave: UIO[Unit]
   * }
   *
   * trait ChatRoom {
   *   def join(username: String): ZIO[Scope, Nothing, Dequeue[UserSession]]
   *   def shutdown(username: String): UIO[Unit]
   * }
   * }}}
   */
  package ChatRoomImpl {

    import zio._

    import java.time.LocalDateTime

    sealed trait ChatEvent {
      def username: String
      def timestamp: LocalDateTime
    }
    object ChatEvent {
      case class UserJoined(username: String, timestamp: LocalDateTime)
          extends ChatEvent
      case class UserLeft(username: String, timestamp: LocalDateTime)
          extends ChatEvent
      case class MessageSent(
        username: String,
        message: String,
        timestamp: LocalDateTime
      ) extends ChatEvent
    }

    trait UserSession {
      def sendMessage(message: String): UIO[Unit]
      def receiveMessages: ZIO[Scope, Nothing, Dequeue[ChatEvent]]
      def leave: UIO[Unit]
    }

    trait ChatRoom {
      def join(username: String): ZIO[Scope, Nothing, UserSession]
      def shutdown: UIO[Unit]
    }

    class UserSessionImpl(
      username: String,
      hub: Hub[ChatEvent],
      subscription: Dequeue[ChatEvent]
    ) extends UserSession {

      override def sendMessage(message: String): UIO[Unit] =
        hub
          .publish(
            ChatEvent.MessageSent(username, message, LocalDateTime.now())
          )
          .unit

      override def receiveMessages: ZIO[Scope, Nothing, Dequeue[ChatEvent]] =
        ZIO.succeed(subscription)

      override def leave: UIO[Unit] =
        hub.publish(ChatEvent.UserLeft(username, LocalDateTime.now())).unit
    }

    object ChatRoom {

      def make: ZIO[Scope, Nothing, ChatRoom] =
        for {
          hub            <- Hub.unbounded[ChatEvent]
          activeUsersRef <- Ref.make(Set.empty[String])
          chatRoom        = new ChatRoomImpl(hub, activeUsersRef)
          _              <- ZIO.addFinalizer(chatRoom.shutdown)
        } yield chatRoom
    }

    class ChatRoomImpl(
      hub: Hub[ChatEvent],
      activeUsersRef: Ref[Set[String]]
    ) extends ChatRoom {

      override def join(username: String): ZIO[Scope, Nothing, UserSession] =
        for {
          _            <- activeUsersRef.update(_ + username)
          _            <- hub.publish(ChatEvent.UserJoined(username, LocalDateTime.now()))
          subscription <- hub.subscribe

          _ <- ZIO.addFinalizer {
                 for {
                   _ <- activeUsersRef.update(_ - username)
                   _ <- subscription.shutdown
                 } yield ()
               }

        } yield new UserSessionImpl(username, hub, subscription)

      def shutdown: UIO[Unit] =
        hub.shutdown
    }

    object ChatRoomExample extends ZIOAppDefault {

      private def simulateUserActivity(
        chatRoom: ChatRoom,
        username: String,
        messages: List[String],
        stayDuration: Duration
      ): ZIO[Any, Nothing, Unit] =
        ZIO.scoped {
          for {
            session  <- chatRoom.join(username)
            receiver <- session.receiveMessages

            _ <- receiver.take.flatMap { event =>
                   val message = event match {
                     case ChatEvent.UserJoined(u, _) =>
                       s"[$username sees] $u joined"
                     case ChatEvent.UserLeft(u, _) =>
                       s"[$username sees] $u left"
                     case ChatEvent.MessageSent(u, msg, _) =>
                       s"[$username sees] $u: $msg"
                   }
                   Console.printLine(message).orDie
                 }
                   .repeatWhile(_ => true)
                   .forkScoped // Fork a fiber to continuously take and print received messages

            _ <- ZIO.foreach(messages) { msg =>
                   for {
                     _ <- session.sendMessage(msg)
                     _ <- Clock.sleep(1.second)
                   } yield ()
                 }

            _ <- Clock.sleep(stayDuration)
            _ <- session.leave // leave the chat

          } yield ()
        }

      override def run =
        ZIO.scoped {
          for {
            chatRoom <- ChatRoom.make

            // Simulate multiple users
            _ <- ZIO.collectAllPar(
                   List(
                     simulateUserActivity(
                       chatRoom,
                       "Alice",
                       List("Hello everyone!", "How's it going?"),
                       5.seconds
                     ),
                     simulateUserActivity(
                       chatRoom,
                       "Bob",
                       List(
                         "Hi Alice!",
                         "I'm doing great!",
                         "Anyone up for a game?"
                       ),
                       7.seconds
                     ).delay(1.second),
                     simulateUserActivity(
                       chatRoom,
                       "Charlie",
                       List("Hey folks!", "Count me in for the game!"),
                       6.seconds
                     ).delay(2.seconds)
                   )
                 )

            _ <-
              Console.printLine(
                "\nChat session completed. Check chat_logs/chat.log for the full log."
              )

          } yield ()
        }
    }

  }

  /**
   *   2. Write a real-time auction system\index{Auction System} that allows
   *      multiple bidders to practice in auctions simultaneously. The auction
   *      system should broadcast bid updates to all participants while
   *      maintaining the strict ordering of bids. Each participant should be
   *      able to place bids and receive updates on the current highest bid:
   *
   * {{{
   * trait AuctionSystem {
   *   def placeBid(
   *     auctionId: String,
   *     bidderId: String,
   *     amount: BigDecimal
   *   ): UIO[Boolean]
   *
   *   def createAuction(
   *     id: String,
   *     startPrice: BigDecimal,
   *     duration: Duration
   *   ): UIO[Unit]
   *
   *   def subscribe: ZIO[Scope, Nothing, Dequeue[AuctionEvent]]
   *
   *   def getAuction(id: String): UIO[Option[AuctionState]]
   * }
   * }}}
   *
   * The core models for the auction system could be as follows:
   *
   * {{{
   * case class Bid(
   *   auctionId: String,
   *   bidderId: String,
   *   amount: BigDecimal,
   *   timestamp: Long
   * )
   *
   * case class AuctionState(
   *   id: String,
   *   currentPrice: BigDecimal,
   *   currentWinner: Option[String],
   *   endTime: Long,
   *   isActive: Boolean
   * )
   *
   * // Events we'll broadcast
   * sealed trait AuctionEvent
   * case class BidPlaced(bid: Bid) extends AuctionEvent
   * case class AuctionEnded(
   *   auctionId: String,
   *   finalPrice: BigDecimal,
   *   winner: Option[String]
   * ) extends AuctionEvent
   * }}}
   */
  package AuctionSystemImpl {

    import zio._

    case class Bid(
      auctionId: String,
      bidderId: String,
      amount: BigDecimal,
      timestamp: Long,
      sequence: Long
    )

    case class AuctionState(
      id: String,
      currentPrice: BigDecimal,
      currentWinner: Option[String],
      endTime: Long,
      isActive: Boolean,
      bidHistory: List[Bid] = Nil
    )

    sealed trait AuctionEvent {
      def sequence: Long
    }
    case class BidPlaced(bid: Bid) extends AuctionEvent {
      def sequence: Long = bid.sequence
    }
    case class AuctionEnded(
      auctionId: String,
      finalPrice: BigDecimal,
      winner: Option[String],
      sequence: Long
    ) extends AuctionEvent
    case class AuctionCreated(
      auctionId: String,
      startPrice: BigDecimal,
      endTime: Long,
      sequence: Long
    ) extends AuctionEvent

    trait AuctionSystem {
      def placeBid(
        auctionId: String,
        bidderId: String,
        amount: BigDecimal
      ): UIO[Boolean]

      def createAuction(
        id: String,
        startPrice: BigDecimal,
        duration: Duration
      ): UIO[Unit]

      def subscribe: ZIO[Scope, Nothing, Dequeue[AuctionEvent]]

      def getAuction(id: String): UIO[Option[AuctionState]]
    }

    case class AuctionSystemState(
      auctions: Map[String, AuctionState],
      eventSequence: Long
    )

    class AuctionSystemLive(
      state: Ref.Synchronized[AuctionSystemState],
      eventHub: Hub[AuctionEvent]
    ) extends AuctionSystem {

      override def placeBid(
        auctionId: String,
        bidderId: String,
        amount: BigDecimal
      ): UIO[Boolean] =
        state.modifyZIO { currentState =>
          val now = java.lang.System.currentTimeMillis()

          currentState.auctions.get(auctionId) match {
            case None =>
              ZIO.succeed((false, currentState))

            case Some(auction) if !auction.isActive =>
              ZIO.succeed((false, currentState))

            case Some(auction) if now > auction.endTime =>
              val nextSeq      = currentState.eventSequence + 1
              val endedAuction = auction.copy(isActive = false)
              val newState = currentState.copy(
                auctions =
                  currentState.auctions.updated(auctionId, endedAuction),
                eventSequence = nextSeq
              )
              val event = AuctionEnded(
                auctionId,
                auction.currentPrice,
                auction.currentWinner,
                nextSeq
              )

              eventHub.publish(event).as((false, newState))

            case Some(auction) if amount <= auction.currentPrice =>
              ZIO.succeed((false, currentState))

            case Some(auction) =>
              val nextSeq = currentState.eventSequence + 1
              val bid     = Bid(auctionId, bidderId, amount, now, nextSeq)

              val updatedAuction = auction.copy(
                currentPrice = amount,
                currentWinner = Some(bidderId),
                bidHistory = bid :: auction.bidHistory
              )

              val newState = AuctionSystemState(
                auctions =
                  currentState.auctions.updated(auctionId, updatedAuction),
                eventSequence = nextSeq
              )

              val event = BidPlaced(bid)

              eventHub.publish(event).as((true, newState))
          }
        }

      override def createAuction(
        id: String,
        startPrice: BigDecimal,
        duration: Duration
      ): UIO[Unit] =
        state.modifyZIO { currentState =>
          val now     = java.lang.System.currentTimeMillis()
          val endTime = now + duration.toMillis
          val nextSeq = currentState.eventSequence + 1

          val auction = AuctionState(
            id = id,
            currentPrice = startPrice,
            currentWinner = None,
            endTime = endTime,
            isActive = true,
            bidHistory = Nil
          )

          val newState = currentState.copy(
            auctions = currentState.auctions.updated(id, auction),
            eventSequence = nextSeq
          )

          val event = AuctionCreated(id, startPrice, endTime, nextSeq)

          eventHub.publish(event).as(((), newState))
        }

      override def subscribe: ZIO[Scope, Nothing, Dequeue[AuctionEvent]] =
        ZIO.acquireRelease(
          eventHub.subscribe
        )(_.shutdown)

      override def getAuction(id: String): UIO[Option[AuctionState]] =
        state.modifyZIO { currentState =>
          val now = java.lang.System.currentTimeMillis()

          currentState.auctions.get(id) match {
            case Some(auction) if auction.isActive && now > auction.endTime =>
              val nextSeq      = currentState.eventSequence + 1
              val endedAuction = auction.copy(isActive = false)
              val newState = currentState.copy(
                auctions = currentState.auctions.updated(id, endedAuction),
                eventSequence = nextSeq
              )
              val event = AuctionEnded(
                id,
                auction.currentPrice,
                auction.currentWinner,
                nextSeq
              )

              eventHub.publish(event).as((Some(endedAuction), newState))

            case auctionOpt =>
              ZIO.succeed((auctionOpt, currentState))
          }
        }
    }

    object AuctionSystemLive {
      val layer: ZLayer[Any, Nothing, AuctionSystem] =
        ZLayer {
          for {
            state    <- Ref.Synchronized.make(AuctionSystemState(Map.empty, 0L))
            eventHub <- Hub.unbounded[AuctionEvent]
          } yield new AuctionSystemLive(state, eventHub)
        }
    }

    object AuctionSystemExample extends ZIOAppDefault {

      override def run = {
        ZIO.scoped {
          for {
            _ <- Console.printLine("Starting Fixed Auction System").orDie
            _ <- for {
                   system <- ZIO.service[AuctionSystem]
                   events <- system.subscribe

                   _ <- events.take.flatMap { event =>
                          Console
                            .printLine(
                              s"Event received (seq=${event.sequence}): $event"
                            )
                            .orDie
                        }.forever.forkScoped

                   _ <- system.createAuction(
                          "item-001",
                          BigDecimal(100),
                          Duration.fromSeconds(60)
                        )
                   _ <- Console.printLine("Auction created for item-001").orDie

                   // Simulate multiple bidders with distinct bid amounts
                   _ <- ZIO.foreachParDiscard(1 to 3) { bidderId =>
                          ZIO.foreachDiscard(1 to 3) { bidNum =>
                            // Ensure unique bid amounts with more variation
                            val amount =
                              BigDecimal(100 + bidderId * 20 + bidNum * 7)
                            for {
                              success <- system.placeBid(
                                           s"item-001",
                                           s"bidder-$bidderId",
                                           amount
                                         )
                              _ <- Console
                                     .printLine(
                                       s"Bidder $bidderId bid $amount: $success"
                                     )
                                     .orDie
                              _ <- ZIO.sleep(
                                     Duration.fromMillis(
                                       100L + (scala.util.Random.nextInt(200))
                                     )
                                   )
                            } yield ()
                          }
                        }

                   _ <- ZIO.sleep(Duration.fromSeconds(2))

                   finalState <- system.getAuction("item-001")
                   _ <-
                     Console.printLine(s"\n=== Final auction state ===").orDie
                   _ <- Console.printLine(s"Final state: $finalState").orDie
                 } yield ()
          } yield ()
        }
      }.provide(AuctionSystemLive.layer)
    }
  }

}
