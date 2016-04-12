import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
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
import scala.language.postfixOps

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

class Kernel extends Actor with ActorLogging {
  val slack = context.actorOf(Props[SlackChatActor], "slack")
  val ims = context.actorOf(Props[IMService], "ims")
  val channels = context.actorOf(Props[ChannelService], "channels")
  val users = context.actorOf(Props[UserService], "users")

  case object EndTrain

  def beerOclock(conductor:String, originalPassengers:Set[String]):Receive = { log.info("state -> beer oclock!")

    system.scheduler.scheduleOnce(4 hours, self, EndTrain)
    var passengers = originalPassengers

    def update(userId:String, channelId:String): Unit = {
      passengers += userId
      slack ! SendMessage(channelId, "it's totally beer o'clock, join the train!")
      (ims ? IMService.OpenIM(userId))
        .mapTo[IMOpened]
        .map {
          case IMOpened(_, imChannelId) =>
            val exceptMe = passengers.filter(_ != userId)
            val c = s"${SlackChatActor.mention(conductor)} is the conductor of today's beer train"
            val message =
              if(exceptMe.nonEmpty) "there are a couple other passengers: " + exceptMe.map(SlackChatActor.mention).mkString(", ")
              else "you're the only passenger so far, " + SlackChatActor.mention(userId)
            SendMessage(imChannelId, Seq(c, message).mkString("\n"))
        }
        .pipeTo(slack)
    }

    {
      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Question(msg), _)
        if userId != conductor && !originalPassengers.contains(userId) =>

        update(userId, sourceChannelId)

      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Answer(msg), _)
        if userId != conductor && !originalPassengers.contains(userId) =>

        update(userId, sourceChannelId)

      case EndTrain => context.become(notBeerOclockYet())
    }
  }

  def notBeerOclockYet():Receive = { log.info("state -> not beer oclock yet")

    var interested = Set[String]()

    {
      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Question(msg), _)  =>
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

      case MessageReceived(ChannelId(sourceChannelId), UserId(userId), Answer(msg), _) =>

        slack ! SendMessage(sourceChannelId, "it's beer o'clock, people! this is not a drill.")

        interested += userId
        val passengers = interested - userId
        context.become(beerOclock(userId, passengers))

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
                  else "no passengers :( looks like you're rolling solo. you do *you*."

                SendMessage(channelId, Seq(train, conductor, ps).mkString("\n"))
            }
            .pipeTo(slack)
        })
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(notBeerOclockYet())
  }}
}

val kernel = system.actorOf(Props[Kernel], "kernel")