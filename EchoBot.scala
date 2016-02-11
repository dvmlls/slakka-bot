import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import akka.pattern.pipe
import slack.ChannelService.{ChannelName, ChannelId}
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.{UserName, UserId}
import slack._
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher
import akka.pattern.ask

case class SendIM(username:String, message:String)
case class ChannelChat(channelName:String, message:String)

class Kernel extends Actor with ActorLogging {
  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val users = context.actorOf(Props[UserService], "users")
  val channels = context.actorOf(Props[ChannelService], "channels")

  rtmStart(Map()).pipeTo(self)

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")
    val Mention = s""".*[<][@]$myUserId[>][: ]+(.+)""".r

    {
      case SendIM(username, message) =>
        for (
          UserService.All(userId, _, _) <- (users ? UserName(username)).mapTo[UserService.All];
          IMOpened(_, channelId) <- (ims ? IMService.OpenIM(userId)).mapTo[IMOpened]
        ) slack ! SendMessage(channelId, message)
      case ChannelChat(channelName, message) =>
        for (
          ChannelService.All(channelId, _) <- (channels ? ChannelName(channelName)).mapTo[ChannelService.All]
        ) slack ! SendMessage(channelId, message)
      case m @ MessageReceived(ChannelId(channelId), UserId(userId), Mention(message)) if message.trim().length > 0 =>
        slack ! SendMessage(channelId, s"no, ${message.trim}")
      case MessageReceived(channel, UserId(userId), message) =>
        (users ? UserId(userId))
          .mapTo[UserService.All]
          .map { case UserService.All(_, userName, _) => MessageReceived(channel, UserName(userName), message) }
          .pipeTo(self)
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMStart(url, RTMSelf(id, name)) =>
      slack ! new URI(url)
      context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")