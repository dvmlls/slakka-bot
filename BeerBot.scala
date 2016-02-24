import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask,pipe}
import slack.ChannelService.ChannelId
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.UserId
import slack._
import slack.SlackWebProtocol._
import Beer._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

class Kernel extends Actor with ActorLogging {
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val channels = context.actorOf(Props[ChannelService], "channels")
  val users = context.actorOf(Props[UserService], "users")

  def beerOclock(interested:Map[String,String]):Receive = { log.info("state -> beer oclock!")

    {
      case _ => ???
    }
  }

  def notBeerOclockYet(myUserId:String, myUserName:String):Receive = { log.info("state -> not beer oclock yet")

    var interested = Map[String, String]()

    {
      case MessageReceived(channel, UserId(userId), message)  =>
        (users ? UserId(userId))
          .mapTo[UserService.All]
          .map { case a => MessageReceived(channel, a, message) }
          .pipeTo(self)
      case MessageReceived(ChannelId(sourceChannelId), UserService.All(userId, username, _), Question(msg)) =>

        interested += userId -> username

        slack ! SendMessage(sourceChannelId, "no, it isn't beer o'clock yet")

        (ims ? IMService.OpenIM(userId))
          .mapTo[IMOpened]
          .map {
            case IMOpened(_, imChannelId) =>
              val exceptMe = interested.filter(_._1 != userId)
              val message =
                if(exceptMe.nonEmpty) "others interested in a beer train: " + exceptMe.values.mkString(", ")
                else "you're the first one aboard the beer train today"
              SendMessage(imChannelId, message)
          }
          .pipeTo(slack)

      case MessageReceived(channel, UserService.All(userId, username, _), Answer(msg)) =>

        (interested.keys ++ Seq(userId)).foreach(uid => {
          (ims ? IMService.OpenIM(uid))
            .mapTo[IMOpened]
            .map {
              case IMOpened(_, channelId) =>
                val messages = Seq(
                  "all aboard the beer train! :engine: :traincar: :traincar: :traincar: :caboose:",
                  s"today's conductor is @$username!"
                )
                val passengers =
                  if (interested.values.nonEmpty) Seq(interested.values.map(u => s"@$u").mkString(", "))
                  else Seq.empty
                SendMessage(channelId, (messages ++ passengers ++ Seq("choo choo!")).mkString("\n"))
            }
            .pipeTo(slack)
        })
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(notBeerOclockYet(id, name))
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")