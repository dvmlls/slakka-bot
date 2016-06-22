package jira

import akka.actor.ActorSystem
import slack.SlackChatActor
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol
import util.MoreJsonProtocols

import scala.concurrent.ExecutionContext

object JiraWebProtocol extends DefaultJsonProtocol with MoreJsonProtocols {
  case class Application(id:String, value:String)
  implicit val applicationFormat = jsonFormat2(Application)
  case class IssueStatus(id:String, name:String)
  implicit val issueStatusFormat = jsonFormat2(IssueStatus)
  case class IssueFields(status:IssueStatus, summary:String, description:Option[String], customfield_10600:List[Application])
  implicit val issueFieldsFormat = jsonFormat4(IssueFields)
  case class Issue(id:String, key:String, fields:IssueFields)
  implicit val issueFormat = jsonFormat3(Issue)

  case class Errors(errorMessages:List[String])
  implicit val errorFormat = jsonFormat1(Errors)

  case class TransitionTransition(id:Int)
  implicit val transitionTransitionFormat = jsonFormat1(TransitionTransition)
  case class Transition(transition:TransitionTransition)
  implicit val transitionFormat = jsonFormat1(Transition)
}

object JiraWebAPI {
  case class UUEncUP(s:String) // uuencoded username:password

  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T], u:UUEncUP) = {
    addHeader("Authorization", s"Basic ${u.s}") ~> sendReceive ~> unmarshal[T]
  }

  // val issues = JiraWebAPI.pipeline[Issue]
  // issues(Get(s"https://wework.atlassian.net/rest/api/2/issue/$issueCode"))


}

object JIRA {
  val ticketPattern = "[A-Z]+[-][0-9]+"
  val TicketPattern = s""".*?($ticketPattern).*""".r
  val CommandPattern = s""".*?(deploy|describe|resolve) ($ticketPattern).*?""".r
}
