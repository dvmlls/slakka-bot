package slack

import java.net.URI
import akka.actor.{Props, ActorLogging, Actor}
import slack.SlackWebProtocol._
import spray.json.{CollectionFormats, DefaultJsonProtocol}
import util.WebSocketClient
import util.WebSocketClient.{Disconnected, Received}

object SlackRTProtocol extends DefaultJsonProtocol with CollectionFormats {
  case class Base(`type`:Option[String])
  implicit val baseFormat = jsonFormat1(Base)

  case class Message(`type`:String, channel:String, text:String, user:Option[String])
  implicit val messageFormat = jsonFormat4(Message)
}

object SlackChatActor {
  case class SendMessage(channel:String, message:String)
  case class MessageReceived(channel:String, from:String, message:String)
}

class SlackChatActor extends Actor with ActorLogging {
  import SlackChatActor._
  import SlackRTProtocol._
  val slackClient = context.actorOf(Props[WebSocketClient], "websocket")

  def connected:Receive = {
    case Received(json) =>
      log.debug("" + json)
      json.convertTo[Base] match {
        case Base(Some("message")) =>
          json.convertTo[Message] match {
            case Message(_, channel, text, Some(user)) => context.parent ! MessageReceived(channel, user, text)
          }
        case _ => context.parent ! json
      }
    case Disconnected() => context.become(disconnected)
    case SendMessage(c, m) =>
      slackClient ! Message("message", c, m, None).toJson
  }

  def disconnected:Receive = {
    case RTMStart(url) =>
      slackClient ! new URI(url)
      context.become(connected)
  }

  def receive = disconnected
}
