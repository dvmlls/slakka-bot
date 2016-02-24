import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask,pipe}
import slack.IMService.IMOpened
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.UserId
import slack._
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

class Kernel extends Actor with ActorLogging {
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val channels = context.actorOf(Props[ChannelService], "channels")
  val users = context.actorOf(Props[UserService], "users")

  val Question = """(?i)(.*is.*it.*beer.*o'?clock.*)""".r
  val Answer = """(?i)(.*(?:it's|its|it is).*beer.*o'?clock.*)""".r

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
      case MessageReceived(channel, UserService.All(userId, username, _), Question(msg)) =>
        (ims ? IMService.OpenIM(userId))
          .mapTo[IMOpened]
          .map {
            case IMOpened(_, channelId) =>
              val message =
                if(interested.nonEmpty) "others interested in a beer train: " + interested.values.mkString(", ")
                else "you're the first one aboard the beer train today"
              SendMessage(channelId, message)
          }
          .pipeTo(slack)

        interested += userId -> username
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
                val passengers = if (interested.values.nonEmpty) Seq(interested.values.map(u => s"@$u").mkString(", "))
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