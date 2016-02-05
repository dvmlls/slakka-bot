import java.io.File
import java.util.concurrent.TimeUnit

import GitActor.RepoCloned
import ProcessActor._
import GithubActor._
import akka.actor._

import scala.concurrent.duration.Duration

object GithubActor {
  case class CheckCIStatus(sha:String)
  trait CIStatus
  case class CISuccess() extends CIStatus
  case class CIFailure() extends CIStatus
  case class CIPending() extends CIStatus
  case class CIError() extends CIStatus
  case class CIUnknown(status:String) extends CIStatus
}

object StatusPoller {
  case class Poll(hub:ActorRef, sha:String)
}

class StatusPoller extends Actor with ActorLogging {
  import StatusPoller._
  implicit val ctx = context.dispatcher

  def pending(hub:ActorRef, sha:String, requester:ActorRef):Receive = { log.debug("state -> pending"); {
    case CIPending() => schedule(hub, sha)
    case u:CIUnknown =>
      context.become(unknown(hub, sha, requester) orElse handleTerminalStates(requester))
      schedule(hub, sha)
  }}

  def handleTerminalStates(requester:ActorRef):Receive = {
    def terminate(a:Any): Unit = {
      requester ! a
      context.become(idle)
    }

    {
      case s:CISuccess => terminate(s)
      case f:CIFailure => terminate(f)
      case e:CIError => terminate(e)
      case x:Status.Failure => terminate(x)
    }
  }

  def unknown(hub:ActorRef, sha:String, requester:ActorRef, retriesLeft:Int=6):Receive = { log.debug("state -> unknown"); {
    case CIPending() =>
      context.become(pending(hub, sha, requester) orElse handleTerminalStates(requester))
      schedule(hub, sha)
    case u:CIUnknown =>
      if (retriesLeft <= 0) {
        context.become(idle)
        requester ! u
      } else {
        context.become(unknown(hub, sha, requester, retriesLeft-1) orElse handleTerminalStates(requester))
        schedule(hub, sha)
      }
  }}

  def schedule(hub:ActorRef, sha:String)  {
    context.system.scheduler.scheduleOnce(Duration.create(10, TimeUnit.SECONDS), hub, CheckCIStatus(sha))
  }

  def idle:Receive = { log.debug("state -> idle"); {
      case Poll(hub, sha) =>
        hub ! CheckCIStatus(sha)
        context.become(unknown(hub, sha, sender()) orElse handleTerminalStates(sender()))
  }}

  def receive = idle
}

class GithubActor extends Actor with ActorLogging {
  import GithubActor._

  val processor = context.actorOf(Props[ProcessActor])

  def checkingCIStatus(requester:ActorRef):Receive = { log.debug("status -> checkingCIStatus")
    var firstLine:Option[String] = None

    {
      case Finished(r:Int) =>
        firstLine.map(_.trim) match {
          case Some("success") => requester ! CISuccess()
          case Some("failure") => requester ! CIFailure()
          case Some("pending") => requester ! CIPending()
          case Some("error") => requester ! CIError()
          case Some(other) => requester ! CIUnknown(other)
          case None => requester ! Status.Failure(new Exception(s"couldn't get status: nothing returned, returnCode=$r"))
        }

        context.unbecome()
      case StdOut(line) if firstLine.isEmpty => firstLine = Some(line.trim)
    }
  }

  def idle(repo:File):Receive = { log.debug("status -> idle"); {
    case CheckCIStatus(sha) =>
      processor ! Request(repo, s"hub ci-status $sha")
      context.become(checkingCIStatus(sender()), discardOld=false)
  }}

  def receive = {
    case RepoCloned(repo) =>
      context.become(idle(repo))
      sender() ! Status.Success("")
  }
}