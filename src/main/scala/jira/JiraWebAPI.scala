package jira

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol
import util.MoreJsonProtocols

import scala.concurrent.ExecutionContext

object JiraWebProtocol extends DefaultJsonProtocol with MoreJsonProtocols {
  case class IssueStatus(id:String, name:String)
  implicit val issueStatusFormat = jsonFormat2(IssueStatus)
  case class IssueFields(status:IssueStatus)
  implicit val issueFieldsFormat = jsonFormat1(IssueFields)
  case class Issue(id:String, key:String, fields:IssueFields)
  implicit val issueFormat = jsonFormat3(Issue)
}

object JiraWebAPI {
  case class UUEncUP(s:String) // uuencoded username:password

  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T], u:UUEncUP) = {
    addHeader("Authorization", s"Basic ${u.s}") ~> sendReceive ~> unmarshal[T]
  }
}
