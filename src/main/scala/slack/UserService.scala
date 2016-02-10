package slack

import akka.actor.{Status, ActorRef, ActorLogging, Actor}
import slack.SlackWebProtocol.{User, UserList}
import akka.pattern.pipe

import scala.collection.mutable

object UserService {
  sealed trait Request
  case class GetIdFromName(name:String) extends Request
  case class GetNameFromId(id:String) extends Request
  case class GotId2Name(id:String, name:String)
}

class UserService extends Actor with ActorLogging {
  import UserService._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val users = SlackWebAPI.createPipeline[UserList]("users.list")

  users(Map()).pipeTo(self)

  def process(r:Request, l:List[User], requester:ActorRef):Unit = {
    requester ! (
      (r match {
        case GetIdFromName(name) => l.find(_.name == name)
        case GetNameFromId(id) => l.find(_.id == id)
      }) match {
        case Some(user) => GotId2Name(user.id, user.name)
        case None => Status.Failure(new Exception(s"couldn't find user given evidence: $r"))
      })
  }

  def processing(l:List[User]):Receive = { log.info("state -> processing"); {
    case r:Request => process(r, l, sender())
  }}

  def queueing:Receive = { log.info("state -> queueing")
    val queue = mutable.Queue[(ActorRef, Request)]()

    {
      case r:Request => queue.enqueue((sender(), r))
      case UserList(l) =>
        queue.foreach { case (requester, name) => process(name, l, requester) }
        context.become(processing(l))
    }
  }

  def receive = queueing
}