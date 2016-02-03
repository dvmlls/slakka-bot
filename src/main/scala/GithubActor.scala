import java.io.File
import java.nio.file.Files

import ProcessActor._
import akka.actor._

import scala.util.{Failure, Success, Try}

object GithubActor {
  case class CloneRepo(org:String, project:String)
  case class RepoCloned(repo:File)
  case class Checkout(branch:String)
  case class Merge(other:String)
  case class AbortMerge()
  case class Push(remote:String)
  case class DeleteBranch(branch:String, remote:String)
  case class CheckCIStatus(sha:String)
  case class CIStatus(status:String)
  case class Succeeded()
  case class GetSHA(branch:String, remote:String)
  case class GotSHA(sha:String)
}

class GithubActor extends Actor with ActorLogging {
  import GithubActor._

  val processor = context.actorOf(Props[ProcessActor])

  def abortingMerge(requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Succeeded()
      context.unbecome() // mergeFailed
      context.unbecome() // merging
      context.unbecome() // ready
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"merge abort failed: $r"))
      context.unbecome()
  }

  def mergeFailed(repo:File):Receive = {
    case AbortMerge =>
      processor ! Request(repo, "git merge --abort")
      context.become(abortingMerge(sender()), discardOld=false)
  }

  def merging(repo:File, requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Succeeded()
      context.unbecome()
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"merge failed: $r"))
      context.become(mergeFailed(repo), discardOld=false)
  }

  def working(requester:ActorRef, failureMessage:String):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Succeeded()
      context.unbecome()
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"$failureMessage: $r"))
      context.unbecome()
  }

  def checkingCIStatus(requester:ActorRef):Receive = {
    var firstLine:Option[String] = None

    {
      case Finished(r:Int) =>

        firstLine match {
          case Some(line) => requester ! CIStatus(line.trim)
          case None => requester ! Status.Failure(new Exception(s"couldn't get status: return code=$r"))
        }

        context.unbecome()
      case StdOut(line) if firstLine.isEmpty => firstLine = Some(line.trim)
    }
  }

  def gettingSHA(requester:ActorRef):Receive = {

    var firstLine:Option[String] = None

    {
      case Finished(r:Int) =>

        firstLine match {
          case Some(line) =>
            line.split(' ') match {
              case Array(_, sha) => requester ! GotSHA(sha)
              case _ => requester ! Status.Failure(new Exception(s"couldn't get sha: couldn't split first line of result"))
            }
          case None => requester ! Status.Failure(new Exception(s"couldn't get sha: return code=$r"))
        }

        context.unbecome()
      case StdOut(line) if firstLine.isEmpty => firstLine = Some(line.trim)
    }
  }

  def ready(org:String, project:String, repo:File):Receive = {
    case Checkout(branch) =>
      processor ! Request(repo, s"git checkout $branch")
      context.become(working(sender(), "checking out failed"), discardOld=false)
    case Merge(other) =>
      processor ! Request(repo, s"git merge --no-ff origin/$other")
      context.become(merging(repo, sender()), discardOld=false)
    case Push(remote) =>
      processor ! Request(repo, s"git push $remote")
      context.become(working(sender(), "pushing failed"), discardOld=false)
    case DeleteBranch(branch, remote) =>
      processor ! Request(repo, s"git push $remote --delete $branch")
      context.become(working(sender(), "deleting branch failed"), discardOld=false)
    case CheckCIStatus(sha) =>
      processor ! Request(repo, s"hub ci-status $sha")
      context.become(checkingCIStatus(sender()), discardOld=false)
    case GetSHA(branch, remote) =>
      processor ! Request(repo, s"git log $remote/$branch -n 1")
      context.become(gettingSHA(sender()), discardOld=false)
  }

  def cloning(org:String, project:String, requester:ActorRef, repo:File):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! RepoCloned(repo)
      context.become(ready(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"clone failed: $r"))
      context.unbecome()
  }

  def empty:Receive = {
    case CloneRepo(org, project) =>
      Try { Files.createTempDirectory("repo").toFile } match {
        case Success(parent) =>
          processor ! Request(parent, s"git clone git@github.com:$org/$project.git")
          context.become(cloning(org, project, sender(), new File(parent, project)), discardOld = false)
        case Failure(ex) => sender() ! Status.Failure(ex)
      }
  }

  def receive = empty
}