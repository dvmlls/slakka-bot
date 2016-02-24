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

  def beerOclock(conductor:String, passengers:Set[String]):Receive = { log.info("state -> beer oclock!")

    {
      case _ => ???
    }
  }

  def notBeerOclockYet(myUserId:String, myUserName:String):Receive = { log.info("state -> not beer oclock yet")

    var interested = Set[String]()

    {
      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Question(msg))  =>
        interested += userId

        slack ! SendMessage(sourceChannelId, "no, it isn't beer o'clock yet")

        (ims ? IMService.OpenIM(userId))
          .mapTo[IMOpened]
          .map {
            case IMOpened(_, imChannelId) =>
              val exceptMe = interested.filter(_ != userId)
              val message =
                if(exceptMe.nonEmpty) "others interested in a beer train: " + exceptMe.map(SlackChatActor.mention).mkString(", ")
                else "you're the first one aboard today's beer train, " + SlackChatActor.mention(userId)
              SendMessage(imChannelId, message)
          }
          .pipeTo(slack)

      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Answer(msg)) =>

        slack ! SendMessage(sourceChannelId, "it's beer o'clock, people! this is not a drill.")

        interested += userId
        val passengers = interested - userId

        interested.foreach(uid => {
          (ims ? IMService.OpenIM(uid))
            .mapTo[IMOpened]
            .map {
              case IMOpened(_, channelId) =>
                val train = "all aboard the beer train! :engine: :traincar: :traincar: :traincar: :caboose: choo choo!"
                val conductor =
                  if (uid == userId) "*you* are the conductor - rally the troops!"
                  else s"today's conductor is ${ SlackChatActor.mention(userId) }!"

                val ps =
                  if (passengers.nonEmpty) "passengers: " + passengers.map(SlackChatActor.mention).mkString(", ")
                  else "no passengers :( looks like you're rolling solo. you do you."

                SendMessage(channelId, Seq(train, conductor, ps).mkString("\n"))
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