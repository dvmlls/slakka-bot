package git

import akka.actor.{Actor, ActorLogging}
import git.StatusActor._
import akka.pattern.pipe

object StatusActor {
  case class CheckCIStatus(org:String, proj:String, sha:String)

  sealed trait CIStatus
  case object CISuccess extends CIStatus
  case object CIFailure extends CIStatus
  case object CIPending extends CIStatus
  case object CIError extends CIStatus
  case class CIUnknown(status:String) extends CIStatus

  def parse(status:GithubWebProtocol.Status):CIStatus = {
    val GithubWebProtocol.Status(s) = status
    s match {
      case "success" => CISuccess
      case "failure" => CIFailure
      case "pending" => CIPending
      case "error" => CIError
      case other => CIUnknown(other)
    }
  }
}

class StatusActor(api:GithubWebAPI) extends Actor with ActorLogging {
  implicit val sys = context.system
  implicit val ctx = context.dispatcher

  def receive = {
    case CheckCIStatus(org, proj, sha) => api.getStatus(org, proj, sha)
      .map { s => log.info("" + s); s }
      .map ( StatusActor.parse )
      .pipeTo(sender())
  }
}