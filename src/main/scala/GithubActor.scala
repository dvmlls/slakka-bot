import java.io.File
import java.nio.file.Files

import ProcessActor._
import akka.actor._

import scala.util.{Failure, Success, Try}

object GithubActor {
  case class CloneRepo(org:String, project:String)
  case class RepoCloned(repo:File)
  case class Checkout(branch:String)
  case class CheckedOut()
  case class Merge(other:String)
  case class Merged()
  case class AbortMerge()
  case class MergeAborted()
  case class Push(remote:String)
  case class Pushed()
  case class DeleteBranch(branch:String, remote:String)
  case class BranchDeleted()
}

class GithubActor extends Actor with ActorLogging {
  import GithubActor._

  val processor = context.actorOf(Props[ProcessActor])

  def abortingMerge(org:String, project:String, repo:File, requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! MergeAborted()
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"merge abort failed: $r"))
      context.become(mergeFailed(org, project, repo))
  }

  def mergeFailed(org:String, project:String, repo:File):Receive = {
    case AbortMerge =>
      processor ! Request(repo, "git merge --abort")
      context.become(abortingMerge(org, project, repo, sender()))
  }

  def merging(org:String, project:String, repo:File, requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Merged()
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"merge failed: $r"))
      context.become(mergeFailed(org, project, repo))
  }

  def checkingOut(org:String, project:String, repo:File, requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! CheckedOut()
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"checkout failed: $r"))
      context.become(cloned(org, project, repo))
  }

  def pushing(org: String, project: String, repo: File, requester: ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Pushed()
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"push failed: $r"))
      context.become(cloned(org, project, repo))
  }

  def deletingBranch(org: String, project: String, repo: File, requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! BranchDeleted()
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"delete failed: $r"))
      context.become(cloned(org, project, repo))
  }

  def cloned(org:String, project:String, repo:File):Receive = {
    case Checkout(branch) =>
      processor ! Request(repo, s"git checkout $branch")
      context.become(checkingOut(org, project, repo, sender()))
    case Merge(other) =>
      processor ! Request(repo, s"git merge --no-ff origin/$other")
      context.become(merging(org, project, repo, sender()))
    case Push(remote) =>
      processor ! Request(repo, s"git push $remote")
      context.become(pushing(org, project, repo, sender()))
    case DeleteBranch(branch, remote) =>
      processor ! Request(repo, s"git push $remote --delete $branch")
      context.become(deletingBranch(org, project, repo, sender()))
  }

  def cloning(org:String, project:String, requester:ActorRef, repo:File):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! RepoCloned(repo)
      context.become(cloned(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"clone failed: $r"))
      context.become(empty)
  }

  def empty:Receive = {
    case CloneRepo(org, project) =>
      Try { Files.createTempDirectory("repo").toFile } match {
        case Success(parent) =>
          processor ! Request(parent, s"git clone git@github.com:$org/$project.git")
          context.become(cloning(org, project, sender(), new File(parent, project)))
        case Failure(ex) => sender() ! Status.Failure(ex)
      }
  }

  def receive = empty
}