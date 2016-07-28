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

implicit val token = SlackWebAPI.Token(sys.env("SLACK_TOKEN"))

val kernel = system.actorOf(Props { new EchoBot() }, "kernel")