package slack

import akka.actor.{Status, ActorRef, ActorLogging, Actor}
import slack.SlackWebProtocol.{User, UserList}
import akka.pattern.pipe

import scala.collection.mutable

object UserService {
  sealed trait UserIdentifier
  sealed trait PartialUserIdentifier extends UserIdentifier
  case class UserName(name:String) extends PartialUserIdentifier
  case class UserId(id:String) extends PartialUserIdentifier
  case class All(id:String, name:String, email:Option[String]) extends UserIdentifier
}

class UserService()(implicit t:SlackWebAPI.Token) extends Actor with ActorLogging {
  import UserService._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val users = SlackWebAPI.createPipeline[UserList]("users.list")

  users(Map()).pipeTo(self)

  def process(r:PartialUserIdentifier, l:List[User], requester:ActorRef):Unit = {
    val result =
      (r match {
        case UserName(name) => l.find(_.name == name)
        case UserId(id) => l.find(_.id == id)
      }) match {
        case Some(user) => All(user.id, user.name, user.profile.email)
        case None => Status.Failure(new Exception(s"couldn't find user given evidence: $r"))
      }

    log.debug(s"mapped: $r -> $result")

    requester ! result
  }

  def processing(l:List[User]):Receive = { log.info("state -> processing"); {
    case r:PartialUserIdentifier => process(r, l, sender())
  }}

  def queueing:Receive = { log.info("state -> queueing")
    val queue = mutable.Queue[(ActorRef, PartialUserIdentifier)]()

    {
      case r:PartialUserIdentifier => queue.enqueue((sender(), r))
      case UserList(l) =>
        queue.foreach { case (requester, name) => process(name, l, requester) }
        context.become(processing(l))
    }
  }

  def receive = queueing
}