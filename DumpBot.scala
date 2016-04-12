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

class Kernel extends Actor with ActorLogging {
  val slack = context.actorOf(Props[SlackChatActor], "slack")

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")

    {
      case MessageReceived(ChannelId(channelId), from, message) if message.contains("dump") =>
        slack ! SendMessage(channelId, s"hehe dump")
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")