package jira

import akka.actor.ActorSystem
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
}

object JiraWebAPI {
  case class UUEncUP(s:String) // uuencoded username:password

  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T], u:UUEncUP) = {
    addHeader("Authorization", s"Basic ${u.s}") ~> sendReceive ~> unmarshal[T]
  }
}