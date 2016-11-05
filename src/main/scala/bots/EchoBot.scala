package bots

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import slack.ChannelService.{ChannelId, ChannelName}
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.SlackWebProtocol.RTMSelf
import slack._
import slack.UserService.{UserId, UserName}

import scala.concurrent.ExecutionContext

sealed trait ChatType
final case class IM(username:String) extends ChatType
final case class Channel(name:String) extends ChatType
final case class Chat(t:ChatType, message:String)

class EchoBot()(implicit t:SlackWebAPI.Token, to:Timeout) extends Actor with ActorLogging {

  implicit val ec:ExecutionContext = context.system.dispatcher

  val slack = context.actorOf(Props { new SlackChatActor() }, "slack")
  val ims = context.actorOf(Props { new IMService() }, "ims")
  val channels = context.actorOf(Props { new ChannelService() }, "channels")
  val users = context.actorOf(Props { new UserService() }, "users")

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")
    val Mention = SlackChatActor.mentionPattern(myUserId)

    {
      case Chat(IM(username), message) =>
        (users ? UserName(username))
          .mapTo[UserService.All]
          .flatMap { case UserService.All(userId, _, _) => (ims ? IMService.OpenIM(userId)).mapTo[IMOpened] }
          .map { case IMOpened(_, channelId) => SendMessage(channelId, message) }
          .pipeTo(slack)
      case Chat(Channel(channelName), message) =>
        (channels ? ChannelName(channelName))
          .mapTo[ChannelService.All]
          .map { case ChannelService.All(channelId, _) => SendMessage(channelId, message)}
          .pipeTo(slack)
      case m @ MessageReceived(ChannelId(channelId), UserId(userId), Mention(message), _) if message.trim().length > 0 =>
        slack ! SendMessage(channelId, s"no, ${message.trim}")
      case MessageReceived(channel, UserId(userId), message, _) =>
        (users ? UserId(userId))
          .mapTo[UserService.All]
          .map { case UserService.All(_, userName, _) => MessageReceived(channel, UserName(userName), message, None) }
          .pipeTo(self)
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  } }
}