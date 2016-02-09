package slack

import akka.actor.{Status, ActorRef, ActorLogging, Actor}
import slack.SlackWebAPI.Request
import slack.SlackWebProtocol.{User, UserList}
import akka.pattern.pipe

import scala.collection.mutable

object UserService {
  case class GetIdFromName(name:String)
  case class GotId2Name(id:String, name:String)
}

class UserService extends Actor with ActorLogging {
  import UserService._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val users = SlackWebAPI.createPipeline[UserList]("users.list")

  users(Request()).pipeTo(self)

  def process(name:String, l:List[User], requester:ActorRef):Unit = {
    requester ! (l.find(_.name == name) match {
      case Some(user) => GotId2Name(user.id, user.name)
      case None => Status.Failure(new Exception(s"couldn't find id given name: $name"))
    })
  }

  def processing(l:List[User]):Receive = { log.info("state -> processing"); {
    case GetIdFromName(name) => process(name, l, sender())
  }}

  def queueing:Receive = { log.info("state -> processing")
    val queue = mutable.Queue[(ActorRef, String)]()

    {
      case GetIdFromName(name) => queue.enqueue((sender(), name))
      case UserList(l) =>
        queue.foreach { case (requester, name) => process(name, l, requester) }
        context.become(processing(l))
    }
  }

  def receive = queueing
}