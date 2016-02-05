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
  type RJF[T] = RootJsonFormat[T]
  implicit def rootEitherFormat[A:RJF,B:RJF] = new RJF[Either[A, B]] {
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

  case class Status(state:String)
  implicit val statusFormat = jsonFormat1(Status)
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

  def createPR(org:String, proj:String, title:String, body:String, source:String, destination:String)
                       (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Post(s"$api/repos/$org/$proj/pulls", CreatePR(title, source, destination, body))
    val p = pipeline[PRCreated]
    p(req)
  }

  def getStatus(org:String, proj:String, sha:String)
               (implicit sys:ActorSystem, cx:ExecutionContext) = {
    val req = Get(s"$api/repos/$org/$proj/commits/$sha/status")
    val p = pipeline[Status]
    p(req)
  }
}