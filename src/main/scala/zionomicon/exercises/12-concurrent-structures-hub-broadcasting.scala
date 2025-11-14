package zionomicon.exercises

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
  package ChatRoomImpl {}

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
  package AuctionSystemImpl {}

}
