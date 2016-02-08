package git

import akka.actor.{Actor, ActorLogging}
import git.StatusActor._
import GithubWebAPI._
import akka.pattern.pipe

object StatusActor {
  case class CheckCIStatus(org:String, proj:String, sha:String)
  trait CIStatus
  case class CISuccess() extends CIStatus
  case class CIFailure() extends CIStatus
  case class CIPending() extends CIStatus
  case class CIError() extends CIStatus
  case class CIUnknown(status:String) extends CIStatus
}

class StatusActor extends Actor with ActorLogging {
  implicit val sys = context.system
  implicit val ctx = context.dispatcher

  def receive = {
    case CheckCIStatus(org, proj, sha) => getStatus(org, proj, sha)
      .map { case GithubWebProtocol.Status(status) => log.info(status); status }
      .map {
        case "success" => CISuccess()
        case "failure" => CIFailure()
        case "pending" => CIPending()
        case "error" => CIError()
        case other => CIUnknown(other)
      }
      .pipeTo(sender())
  }
}