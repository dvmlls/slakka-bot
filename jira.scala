import java.util.concurrent.TimeUnit
import scala.util.{Success, Failure}
import akka.util.Timeout
import akka.actor.{Props, ActorLogging, Actor, ActorSystem}
import jira.JiraWebAPI
import jira.JiraWebAPI.UUEncUP
import jira.JiraWebProtocol._
import slack.ChannelService.ChannelId
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.SlackWebProtocol.RTMSelf
import slack.UserService.UserId
import slack._
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
implicit val d = system.dispatcher
implicit val u = UUEncUP(sys.env("JIRA_PASSWORD"))
implicit val token = SlackWebAPI.Token(sys.env("SLACK_TOKEN"))

val p = JiraWebAPI.pipeline[Issue]
val JIRAPattern = """.*?([A-Z]+[-][0-9]+).*""".r

class JiraBot extends Actor with ActorLogging {
  val slack = context.actorOf(Props { new SlackChatActor() }, "slack")
  val ims = context.actorOf(Props { new IMService() }, "ims")
  val channels = context.actorOf(Props { new ChannelService() }, "channels")
  val users = context.actorOf(Props { new UserService() }, "users")

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")
    val Mention = SlackChatActor.mentionPattern(myUserId)

    {
      case MessageReceived(ChannelId(channelId), UserId(userId), Mention(JIRAPattern(issueCode)), _) =>
        p(Get(s"https://wework.atlassian.net/rest/api/2/issue/$issueCode")).onComplete {
          case Success(issue) =>
            log.info("" + issue)
            val d = issue.fields.description.map (_.split('\n').map("\n> " + _).mkString(""))
            val s = s"*$issueCode* _[${issue.fields.status.name}]_ ${issue.fields.summary}" + d.getOrElse("")
            slack ! SendMessage(channelId, s)
          case Failure(ex) =>
            log.warning(issueCode, ex)
            val s = s"[*$issueCode*] error: ${ex.getMessage}"
            slack ! SendMessage(channelId, s)
        }
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[JiraBot], "kernel")