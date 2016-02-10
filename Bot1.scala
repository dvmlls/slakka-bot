import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import akka.pattern.pipe
import slack.IMService.IMOpened
import slack.SlackChatActor.SendMessage
import slack.UserService.GotId2Name
import slack.{UserService, IMService, SlackChatActor, SlackWebAPI}
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher
import akka.pattern.ask

case class SendIM(username:String, message:String)

class Kernel extends Actor with ActorLogging {
  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val users = context.actorOf(Props[UserService], "users")

  rtmStart(Map()).pipeTo(self)

  def connected:Receive = { log.info("connected"); {
    case SendIM(username, message) =>
      for (
        GotId2Name(userId, _) <- (users ? UserService.GetIdFromName(username)).mapTo[GotId2Name];
        IMOpened(_, channelId) <- (ims ? IMService.OpenIM(userId)).mapTo[IMOpened]
      ) slack ! SendMessage(channelId, message)
  }}

  def receive:Receive = { log.info("disconnected"); {
    case r:RTMStart =>
      slack ! r
      context.become(connected)
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")
