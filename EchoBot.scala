import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask,pipe}
import slack.ChannelService.{ChannelName, ChannelId}
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.{UserName, UserId}
import slack._
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

sealed trait ChatType
case class IM(username:String) extends ChatType
case class Channel(name:String) extends ChatType
case class Chat(t:ChatType, message:String)

class Kernel extends Actor with ActorLogging {
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val channels = context.actorOf(Props[ChannelService], "channels")
  val users = context.actorOf(Props[UserService], "users")

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
    case RTMStart(url, RTMSelf(id, name)) => context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")