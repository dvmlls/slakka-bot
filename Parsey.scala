import java.io.File
import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import slack.ChannelService.ChannelId
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.UserId
import slack._
import slack.SlackWebProtocol._
import util.ProcessActor2
import util.ProcessActor2._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

sealed trait ChatType
case class IM(username:String) extends ChatType
case class Channel(name:String) extends ChatType
case class Chat(t:ChatType, message:String)

implicit val token = SlackWebAPI.Token(sys.env("PARSEY_TOKEN"))
implicit val path = sys.env("PARSEY_PATH")

case class ParseRequest(channelId:String, input:String)
case class ParseResult(channelId:String, output:String)

class SingleParse extends Actor with ActorLogging {

  def parsing(channelId:String, input:String, requester:ActorRef):Receive = {

    log.info(s"parsing: channelId=$channelId input=$input")

    val process = context.actorOf(Props[ProcessActor2])
    process ! ProcessActor2.Run(List("syntaxnet/demo.sh"), Some(new File(path)))
    process ! WriteLine(input)
    process ! Close()

    val results = scala.collection.mutable.Buffer[String]()

    {
      case StdOut(s) => results.append(s)
      case StdErr(s) => log.info(s)
      case Finished(returnCode) =>
        log.info(s"exit code: $returnCode")
        requester ! ParseResult(channelId, results.mkString("\n"))
        self ! PoisonPill
    }
  }

  def receive:Receive = {
    case ParseRequest(channelId, input) => context.become(parsing(channelId, input, sender()))
  }
}

class ParseyBot extends Actor with ActorLogging {
  val slack = context.actorOf(Props { new SlackChatActor() }, "slack")

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")
    val Mention = SlackChatActor.mentionPattern(myUserId)
    var parses = 0

    {
      case m @ MessageReceived(ChannelId(channelId), UserId(userId), Mention(message), _) if message.trim().length > 0 =>
        val parser = context.actorOf(Props[SingleParse], s"parser$parses")
        parses = parses + 1
        parser ! ParseRequest(channelId, message)

      case ParseResult(channelId, output) =>
        slack ! SendMessage(channelId, s"```$output```")
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[ParseyBot], "kernel")