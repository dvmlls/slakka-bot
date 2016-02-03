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
  case class CheckCIStatus()
  case class CIStatus(status:String)
  case class Succeeded()
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