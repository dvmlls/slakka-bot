import akka.actor.ActorSystem
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
  case class MergeRequest(commit_message:String, sha:String)
  implicit val mergeRequestFormat = jsonFormat2(MergeRequest)
  case class MergeSuccess(message:String, merged:Boolean, sha:String)
  case class MergeFailure(message:String, documentation_url:String)
  implicit val mergeSuccessFormat = jsonFormat3(MergeSuccess)
  implicit val mergeFailureFormat = jsonFormat2(MergeFailure)

  case class PullRequestHead(ref:String, sha:String)
  case class PullRequest(number:Int, state:String, head:PullRequestHead, mergeable:Option[Boolean], merged:Boolean)
  implicit val pullRequestHeadFormat = jsonFormat2(PullRequestHead)
  implicit val pullRequestFormat = jsonFormat5(PullRequest)

  case class CreatePullRequest(title:String, head:String, base:String, body:String)
  case class PullRequestCreated(number:Int)
  implicit val createPullRequestFormat = jsonFormat4(CreatePullRequest)
  implicit val pullRequestCreatedFormat = jsonFormat1(PullRequestCreated)
}

object GithubWebAPI {
  import GithubWebProtocol._

  val api = "https://api.github.com"
  def pipeline[T](implicit system:ActorSystem, cx:ExecutionContext, um:FromResponseUnmarshaller[T]) = {
    addHeader("Authorization", s"token ${sys.env("GITHUB_TOKEN")}") ~>
      sendReceive ~>
      unmarshal[T]
  }

  def mergePullRequest(org:String, proj:String, number:Int, sha:String)
                      (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Put(s"$api/repos/$org/$proj/pulls/$number/merge", MergeRequest("", sha))
    val p = pipeline[Either[MergeFailure, MergeSuccess]]
    p(req)
  }

  def getPullRequest(org:String, proj:String, number:Int)
                    (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/pulls/$number")
    val p = pipeline[PullRequest]
    p(req)
  }

  def createPullRequest(org:String, proj:String, title:String, body:String, head:String, base:String)
                       (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Post(s"$api/repos/$org/$proj/pulls", CreatePullRequest(title, head, base, body))
    val p = pipeline[PullRequestCreated]
    p(req)
  }
}

object GithubWebAPITester extends App {

  implicit val system = ActorSystem()
  import system.dispatcher

  GithubWebAPI
    .getPullRequest("dvmlls", "slakka-bot", 16)
    .onComplete {
      case a:Any =>
        println(a)
        system.terminate()
        sys.exit()
    }
}