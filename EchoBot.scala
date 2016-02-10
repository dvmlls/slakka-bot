import java.net.URI
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import akka.pattern.pipe
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.{UserName, UserId, All}
import slack.{UserService, IMService, SlackChatActor, SlackWebAPI}
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher
import akka.pattern.ask

val authorized = List("dave")

case class SendIM(username:String, message:String)

class Kernel extends Actor with ActorLogging {
  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val users = context.actorOf(Props[UserService], "users")

  rtmStart(Map()).pipeTo(self)

  def connected(myUserId:String, myUserName:String):Receive = { log.info("connected")
    val Mention = s""".*[<][@]$myUserId[>][: ]+(.+)""".r

    {
      case SendIM(username, message) =>
        for (
          All(userId, _, _) <- (users ? UserService.UserName(username)).mapTo[All];
          IMOpened(_, channelId) <- (ims ? IMService.OpenIM(userId)).mapTo[IMOpened]
        ) slack ! SendMessage(channelId, message)
      case m @ MessageReceived(channelId, UserId(userId), Mention(message)) if message.trim().length > 0 =>
        (users ? UserId(userId)).mapTo[All]
          .map { case All(_, name, _) => MessageReceived(channelId, UserName(name), message) }
            .pipeTo(self)
      case m @ MessageReceived(channelId, UserName(username), message)
          if authorized.nonEmpty && authorized.contains(username) =>
        slack ! SendMessage(channelId, s"no, $message")
    }
  }

  def receive:Receive = { log.info("disconnected"); {
    case RTMStart(url, RTMSelf(id, name)) =>
      slack ! new URI(url)
      context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")