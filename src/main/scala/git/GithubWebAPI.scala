package git

import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem

import spray.client.pipelining._
import spray.httpx.unmarshalling._
import spray.httpx.SprayJsonSupport._

object GithubWebAPI {
  case class Token(s:String)
}

/*
 * https://developer.github.com/v3/
 */
trait GithubWebAPI {
  import GithubWebProtocol._
  import GithubWebAPI._

  implicit def system:ActorSystem
  implicit def token:Token
  implicit def ec:ExecutionContext = system.dispatcher

  val api = "https://api.github.com"
  def pipeline[T](implicit um:FromResponseUnmarshaller[T]) = {
    addHeader("Authorization", s"token ${ token.s }") ~>
      addHeader("Accept", "application/vnd.github.v3+json") ~> // github advises requesting the version explicitly
      sendReceive ~>
      unmarshal[T]
  }

  def mergePR(org:String, proj:String, number:Int, sha:String) = {
    val req = Put(s"$api/repos/$org/$proj/pulls/$number/merge", MergePR("", sha))
    val p = pipeline[Either[MergePRFailure, MergePRSuccess]]
    p(req)
  }

  def getPR(org:String, proj:String, number:Int) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number")
    val p = pipeline[PR]
    p(req)
  }

  def createPR(org:String, proj:String, title:String, body:String, source:String, destination:String) = {
    val req = Post(s"$api/repos/$org/$proj/pulls", CreatePR(title, source, destination, body))
    val p = pipeline[PRCreated]
    p(req)
  }

  def getStatus(org:String, proj:String, sha:String) = {
    val req = Get(s"$api/repos/$org/$proj/commits/$sha/status")
    val p = pipeline[Status]
    p(req)
  }

  def comment(org:String, proj:String, issue:Int, body:String) = {
    val req = Post(s"$api/repos/$org/$proj/issues/$issue/comments", CommentOnIssue(body))
    val p = pipeline[CommentedOnIssue]
    p(req)
  }

  def getIssueComments(org:String, proj:String, number:Int) = {
    val req = Get(s"$api/repos/$org/$proj/issues/$number/comments")
    val p = pipeline[List[IssueComment]]
    p(req)
  }

  def getReviewComments(org:String, proj:String, number:Int) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number/comments")
    val p = pipeline[List[PRReviewComment]]
    p(req)
  }

  def getCommits(org:String, proj:String, number:Int) = {
    /*
     * question: should this query the git log instead of the github api?
     */
    val req = Get(s"$api/repos/$org/$proj/pulls/$number/commits")
    val p = pipeline[List[PRCommit]]
    p(req)
  }

  def listPulls(org:String, proj:String) = {
    val req = Get(s"$api/repos/$org/$proj/pulls?state=open")
    val p = pipeline[List[PR]]
    p(req)
  }

  def listContents(org:String, proj:String, path:String) = {
    val req = Get(s"$api/repos/$org/$proj/contents/$path")
    val p = pipeline[List[File]]
    p(req)
  }
}