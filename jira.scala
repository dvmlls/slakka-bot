import java.util.concurrent.TimeUnit

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
import spray.http.HttpResponse
import spray.httpx.SprayJsonSupport._
import jira.JIRA.CommandPattern

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
implicit val d = system.dispatcher
implicit val u = UUEncUP(sys.env("JIRA_PASSWORD"))
implicit val token = SlackWebAPI.Token(sys.env("SOXBOT_TOKEN"))

class JiraBot extends Actor with ActorLogging {
  val issues = JiraWebAPI.pipeline[Issue]
  val transitions = JiraWebAPI.pipeline[HttpResponse]

  val slack = context.actorOf(Props { new SlackChatActor() }, "slack")
  val ims = context.actorOf(Props { new IMService() }, "ims")
  val channels = context.actorOf(Props { new ChannelService() }, "channels")
  val users = context.actorOf(Props { new UserService() }, "users")

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected")
    val Mention = SlackChatActor.mentionPattern(myUserId)

    {
      case MessageReceived(ChannelId(channelId), UserId(userId), Mention(CommandPattern("resolve", issueCode)), _) =>
        val uri = s"https://wework.atlassian.net/rest/api/2/issue/$issueCode/transitions"
        val f = transitions(Post(uri, Transition(TransitionTransition(71))))

        f.onFailure {
          case ex =>
            log.warning(issueCode, ex)
            val s = s"[*$issueCode*] error: ${ex.getMessage}"
            slack ! SendMessage(channelId, s)
        }

        f.foreach {
          case a:Any =>
            log.info(s"[*$issueCode*] success: $a")

//            val d = issue.fields.description.map (_.split('\n').map("\n> " + _).mkString(""))
//            val s = s"*$issueCode* _[${issue.fields.status.name}]_ ${issue.fields.summary}" + d.getOrElse("")
//            slack ! SendMessage(channelId, s)

        }


      case MessageReceived(ChannelId(channelId), UserId(userId), Mention(CommandPattern("describe", issueCode)), _) =>

        val f = for (
          issue <- issues(Get(s"https://wework.atlassian.net/rest/api/2/issue/$issueCode"))
        ) yield issue

        f.onFailure {
          case ex =>
            log.warning(issueCode, ex)
            val s = s"[*$issueCode*] error: ${ex.getMessage}"
            slack ! SendMessage(channelId, s)
        }

        f.foreach {
          case issue =>
            log.info("" + issue)
            val d = issue.fields.description.map (_.split('\n').map("\n> " + _).mkString(""))
            val s = s"*$issueCode* _[${issue.fields.status.name}]_ ${issue.fields.summary}" + d.getOrElse("")
            slack ! SendMessage(channelId, s)
        }
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  }}
}

val kernel = system.actorOf(Props[JiraBot], "kernel")