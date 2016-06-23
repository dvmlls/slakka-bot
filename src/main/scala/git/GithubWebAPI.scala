package git

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.httpx.unmarshalling._
import scala.concurrent.ExecutionContext
import spray.httpx.SprayJsonSupport._

/*
 * https://developer.github.com/v3/
 */
object GithubWebAPI {
  import GithubWebProtocol._

  val api = "https://api.github.com"
  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T]) = {
    addHeader("Authorization", s"token ${sys.env("GITHUB_TOKEN")}") ~>
      addHeader("Accept", "application/vnd.github.v3+json") ~> // github advises requesting the version explicitly
      sendReceive ~>
      unmarshal[T]
  }

  def mergePR(org:String, proj:String, number:Int, sha:String) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Put(s"$api/repos/$org/$proj/pulls/$number/merge", MergePR("", sha))
    val p = pipeline[Either[MergePRFailure, MergePRSuccess]]
    p(req)
  }

  def getPR(org:String, proj:String, number:Int) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number")
    val p = pipeline[PR]
    p(req)
  }

  def createPR(org:String, proj:String, title:String, body:String, source:String, destination:String)
              (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Post(s"$api/repos/$org/$proj/pulls", CreatePR(title, source, destination, body))
    val p = pipeline[PRCreated]
    p(req)
  }

  def getStatus(org:String, proj:String, sha:String) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/commits/$sha/status")
    val p = pipeline[Status]
    p(req)
  }

  def comment(org:String, proj:String, issue:Int, body:String) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Post(s"$api/repos/$org/$proj/issues/$issue/comments", CommentOnIssue(body))
    val p = pipeline[CommentedOnIssue]
    p(req)
  }

  def getComments(org:String, proj:String, number:Int) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number/comments")
    val p = pipeline[List[PRComment]]
    p(req)
  }

  def getCommits(org:String, proj:String, number:Int) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    /*
     * question: should this query the git log instead of the github api?
     */
    val req = Get(s"$api/repos/$org/$proj/pulls/$number/commits")
    val p = pipeline[List[PRCommit]]
    p(req)
  }

  def listPulls(org:String, proj:String) (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/pulls?state=open")
    val p = pipeline[List[PR]]
    p(req)
  }
}