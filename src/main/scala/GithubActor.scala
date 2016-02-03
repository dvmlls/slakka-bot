import java.io.File

import GitActor.RepoCloned
import ProcessActor._
import akka.actor._

import scala.util.Success

object GithubActor {
  case class CheckCIStatus(sha:String)
  case class CIStatus(status:String)
  case class PullRequest(title:String, org:String, proj:String, from:String, to:String)
  case class PullRequested(url:String)
}

class GithubActor extends Actor with ActorLogging {
  import GithubActor._

  val processor = context.actorOf(Props[ProcessActor])

  def checkingCIStatus(requester:ActorRef):Receive = {
    var firstLine:Option[String] = None

    {
      case Finished(r:Int) if r == 0 =>

        firstLine match {
          case Some(line) => requester ! CIStatus(line.trim)
          case None => requester ! Status.Failure(new Exception(s"couldn't get sha: nothing returned"))
        }

        context.unbecome()
      case Finished(r:Int) if r != 0 =>
        requester ! Status.Failure(new Exception(s"couldn't get sha: return code=$r"))
        context.unbecome()
      case StdOut(line) if firstLine.isEmpty => firstLine = Some(line.trim)
    }
  }

  def idle(repo:File):Receive = {
    case CheckCIStatus(sha) =>
      processor ! Request(repo, s"hub ci-status $sha")
      context.become(checkingCIStatus(sender()), discardOld=false)
  }

  def receive = {
    case RepoCloned(repo) =>
      context.become(idle(repo))
      sender() ! Success()
  }
}