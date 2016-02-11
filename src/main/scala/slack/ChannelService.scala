package slack

import akka.actor.{Status, ActorRef, ActorLogging, Actor}
import slack.SlackWebProtocol.{Channel, ChannelList}
import akka.pattern.pipe

import scala.collection.mutable

object ChannelService {
  sealed trait ChannelIdentifier
  sealed trait PartialChannelIdentifier extends ChannelIdentifier
  case class ChannelName(name:String) extends PartialChannelIdentifier
  case class ChannelId(id:String) extends PartialChannelIdentifier
  case class All(id:String, name:String) extends ChannelIdentifier
}

class ChannelService extends Actor with ActorLogging {
  import ChannelService._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val channels = SlackWebAPI.createPipeline[ChannelList]("channels.list")

  channels(Map("excludeArchived" -> "1")).pipeTo(self)

  def process(r:PartialChannelIdentifier, l:List[Channel], requester:ActorRef):Unit = {
    val result =
      (r match {
        case ChannelName(name) => l.find(_.name == name)
        case ChannelId(id) => l.find(_.id == id)
      }) match {
        case Some(user) => All(user.id, user.name)
        case None => Status.Failure(new Exception(s"couldn't find channel given evidence: $r"))
      }

    log.debug(s"mapped: $r -> $result")

    requester ! result
  }

  def processing(l:List[Channel]):Receive = { log.info("state -> processing"); {
    case r:PartialChannelIdentifier => process(r, l, sender())
  }}

  def queueing:Receive = { log.info("state -> queueing")
    val queue = mutable.Queue[(ActorRef, PartialChannelIdentifier)]()

    {
      case r:PartialChannelIdentifier => queue.enqueue((sender(), r))
      case ChannelList(l) =>
        queue.foreach { case (requester, name) => process(name, l, requester) }
        context.become(processing(l))
    }
  }

  def receive = queueing
}