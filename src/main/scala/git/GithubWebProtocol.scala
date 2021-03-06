package git

import java.time.Instant

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
  case class PR(title:String, number:Int, state:String, body:String, head:PRHead, mergeable:Option[Boolean], merged:Option[Boolean])
  implicit val PRHeadFormat = jsonFormat2(PRHead)
  implicit val PRFormat = jsonFormat7(PR)

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

  case class PRUser(login:String)
  implicit val prUserFormat = jsonFormat1(PRUser)

  case class PRReviewComment(body:String, user:PRUser, updated_at:Instant, commit_id:String)
  implicit val prReviewCommentFormat = jsonFormat4(PRReviewComment)

  case class PRCommitCommitter(date:Instant)
  implicit val prCommitCommitterFormat = jsonFormat1(PRCommitCommitter)
  case class PRCommitCommit(committer:PRCommitCommitter)
  implicit val prCommitCommitFormat = jsonFormat1(PRCommitCommit)
  case class PRCommit(sha:String, committer:PRUser, commit:PRCommitCommit)
  implicit val prCommitFormat = jsonFormat3(PRCommit)

  case class IssueComment(id:Int, body:String, user:PRUser, updated_at:Instant)
  implicit val issueCommentFormat = jsonFormat4(IssueComment)

  /* https://developer.github.com/v3/repos/contents/ */
  case class File(name:String)
  implicit val contentListingFormat = jsonFormat1(File)
}