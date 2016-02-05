import java.util.concurrent.TimeUnit

import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import spray.client.pipelining._
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.json._
import spray.httpx.SprayJsonSupport._
import scala.concurrent.ExecutionContext

trait MoreJsonProtocols {
  /*
   * http://stackoverflow.com/a/25417819/908042
   */
  implicit def rootEitherFormat[A : RootJsonFormat, B : RootJsonFormat] = new RootJsonFormat[Either[A, B]] {
    val format = DefaultJsonProtocol.eitherFormat[A, B]
    def write(either: Either[A, B]) = format.write(either)
    def read(value: JsValue) = format.read(value)
  }
}

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
}

object GithubWebAPI {
  import GithubWebProtocol._

  val api = "https://api.github.com"
  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T]) = {
    addHeader("Authorization", s"token ${sys.env("GITHUB_TOKEN")}") ~>
      sendReceive ~>
      unmarshal[T]
  }

  def mergePR(org:String, proj:String, number:Int, sha:String)
                      (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Put(s"$api/repos/$org/$proj/pulls/$number/merge", MergePR("", sha))
    val p = pipeline[Either[MergePRFailure, MergePRSuccess]]
    p(req)
  }

  def getPR(org:String, proj:String, number:Int)
                    (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number")
    val p = pipeline[PR]
    p(req)
  }

  def createPR(org:String, proj:String, title:String, body:String, head:String, base:String)
                       (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Post(s"$api/repos/$org/$proj/pulls", CreatePR(title, head, base, body))
    val p = pipeline[PRCreated]
    p(req)
  }
}

object GithubWebAPITester extends App {

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)

  val org = "dvmlls"
  val proj = "slakka-bot"
  val branch = "feature/web_pull_requests"

  import GitActor._
  import GithubActor._
  import Autobot._

  val g = system.actorOf(Props[GitActor])
  val h = system.actorOf(Props[GithubActor])
  val p = system.actorOf(Props[StatusPoller])

  import akka.pattern.ask

  val f = for (
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    _ <- h ? RepoCloned(repo);
    poll = (sha:String) => (p ? StatusPoller.Poll(h, sha)).mapTo[CIStatus];
    (branchName, branchSha, branchResult) <- autoMerge(org, proj, 20, poll);
    _ <- g ? DeleteBranch(branchName, "origin")
  ) yield (branchName, branchSha, branchResult)

  f.onComplete {
    case a:Any => println(a)
      println(a)
      system.terminate()
      sys.exit()
  }
}