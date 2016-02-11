package slack

import java.net.URI
import akka.actor.{Props, ActorLogging, Actor}
import slack.ChannelService.{ChannelId, ChannelIdentifier}
import slack.UserService.{UserId, UserIdentifier}
import spray.json.{CollectionFormats, DefaultJsonProtocol}
import util.WebSocketClient
import util.WebSocketClient.{Disconnected, Received}

object SlackRTProtocol extends DefaultJsonProtocol with CollectionFormats {
  case class Base(`type`:Option[String])
  implicit val baseFormat = jsonFormat1(Base)

  case class Message(`type`:String, channel:String, text:Option[String], user:Option[String])
  implicit val messageFormat = jsonFormat4(Message)
}

object SlackChatActor {
  case class SendMessage(channel:String, message:String)
  case class MessageReceived(channel:ChannelIdentifier, from:UserIdentifier, message:String)
}

class SlackChatActor extends Actor with ActorLogging {
  import SlackChatActor._
  import SlackRTProtocol._
  val slackClient = context.actorOf(Props[WebSocketClient], "websocket")

  def connected:Receive = { log.info("state -> connected"); {
    case Received(json) =>
      log.debug("" + json)
      json.convertTo[Base] match {
        case Base(Some("message")) =>
          json.convertTo[Message] match {
            case Message(_, channel, Some(text), Some(user)) =>
              context.parent ! MessageReceived(ChannelId(channel), UserId(user), text)
            case m:Message => log.debug(s"received message I couldn't parse: $m")
          }
        case _ => context.parent ! json
      }
    case Disconnected() => context.become(disconnected)
    case SendMessage(c, m) => slackClient ! Message("message", c, Some(m), None).toJson
  }}

  def disconnected:Receive = { log.info("state -> disconnected"); {
    case uri:URI =>
      slackClient ! uri
      context.become(connected)
  }}

  def receive = disconnected
}