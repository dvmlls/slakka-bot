package slack

import java.net.URI
import akka.actor._
import akka.pattern.pipe
import akka.actor.SupervisorStrategy.Stop
import spray.json.{CollectionFormats, DefaultJsonProtocol, JsValue}
import slack.ChannelService.{ChannelId, ChannelIdentifier}
import slack.SlackWebProtocol.{RTMSelf, RTMStart}
import slack.UserService.{UserId, UserIdentifier}
import util.WebSocketClient
import util.WebSocketClient.{Disconnected, Received}

object SlackRTProtocol extends DefaultJsonProtocol with CollectionFormats {
  case class Base(`type`:Option[String])
  implicit val baseFormat = jsonFormat1(Base)

  case class Message(`type`:String, channel:String, text:Option[String], user:Option[String], ts:Option[String])
  implicit val messageFormat = jsonFormat5(Message)
}

object SlackChatActor {
  case class SendMessage(channel:String, message:String)
  case class MessageReceived(channel:ChannelIdentifier, from:UserIdentifier, message:String, ts:Option[String])
  def mentionPattern(userId:String) = s""".*[<][@]$userId[>][: ]+(.+)""".r
  def mention(userId:String) = s"<@$userId>"
}

object MessageMatcher {
  import SlackRTProtocol._
  import SlackChatActor._
  def unapply(json:JsValue):Option[MessageReceived] = {
    json.convertTo[Base] match {
      case Base(Some("message")) =>
        json.convertTo[Message] match {
          case Message(_, c, Some(msg), Some(u), t) => Some(MessageReceived(ChannelId(c), UserId(u), msg, t))
          case _ => None
        }
      case _ => None
    }
  }
}

class SlackChatActor(target:Option[ActorRef] = None)(implicit t:SlackWebAPI.Token) extends Actor with ActorLogging {
  implicit val sys = context.system
  implicit val ec = sys.dispatcher
  import SlackChatActor._
  import SlackRTProtocol._

  override val supervisorStrategy = OneForOneStrategy() { case _ => Stop }

  def connected(slackClient:ActorRef):Receive = { log.info("state -> connected"); {
    case Received(MessageMatcher(m)) => target.getOrElse(context.parent) ! m
    case Disconnected() => context.become(disconnected)
    case SendMessage(c, m) => slackClient ! Message("message", c, Some(m), None, None).toJson
    case Terminated(who) =>
      log.warning(s"slack client disconnected: $who")
      context.become(disconnected)
  }}

  def disconnected:Receive = {
    log.info("state -> disconnected")
    val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
    rtmStart(Map()).pipeTo(self)

    {
      case RTMStart(url, RTMSelf(id, name)) =>
        val slackClient = context.actorOf(Props[WebSocketClient], "wsclient")
        slackClient ! new URI(url)
        target.getOrElse(context.parent) ! RTMSelf(id, name)
        context.watch(slackClient)
        context.become(connected(slackClient))
    }
  }

  def receive = disconnected
}