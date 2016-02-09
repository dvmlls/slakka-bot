package git

import spray.json.DefaultJsonProtocol
import util.MoreJsonProtocols

object GithubWebProtocol extends DefaultJsonProtocol with MoreJsonProtocols {
  case class MergePR(commit_message:String, sha:String)
  implicit val mergePRFormat = jsonFormat2(MergePR)
  case class MergePRSuccess(message:String, merged:Boolean, sha:String)
  case class MergePRFailure(message:String, documentation_url:String)
  implicit val mergePRSuccessFormat = jsonFormat3(MergePRSuccess)
  implicit val mergePRFailureFormat = jsonFormat2(MergePRFailure)

  case class PRHead(ref:String, sha:String)
  case class PR(number:Int, state:String, head:PRHead, mergeable:Option[Boolean], merged:Boolean)
  implicit val PRHeadFormat = jsonFormat2(PRHead)
  implicit val PRFormat = jsonFormat5(PR)

  case class CreatePR(title:String, head:String, base:String, body:String)
  case class PRCreated(number:Int)
  implicit val createPRFormat = jsonFormat4(CreatePR)
  implicit val PRCreatedFormat = jsonFormat1(PRCreated)

  case class Status(state:String)
  implicit val statusFormat = jsonFormat1(Status)

  case class CommentOnIssue(body:String)
  implicit val commentOnIssueFormat = jsonFormat1(CommentOnIssue)
  case class CommentedOnIssue(html_url:String)
  implicit val commentedOnIssueFormat = jsonFormat1(CommentedOnIssue)
}