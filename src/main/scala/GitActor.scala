import java.io.File
import java.nio.file.Files

import ProcessActor._
import akka.actor._

import scala.util.{Failure, Success, Try}

object GitActor {
  case class CloneRepo(org:String, project:String)
  case class RepoCloned(repo:File)
  case class Checkout(branch:String)
  case class Merge(other:String)
  case class AbortMerge()
  case class Push(remote:String)
  case class DeleteBranch(branch:String, remote:String)
  case class GetSHA(branch:String, remote:String)
  case class GotSHA(sha:String)
  case class AddRemote(name:String, path:String)
}

class GitActor extends Actor with ActorLogging {
  import GitActor._

  val processor = context.actorOf(Props[ProcessActor])

  def abortingMerge(requester:ActorRef):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Status.Success("")
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
      requester ! Status.Success("")
      context.unbecome()
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"merge failed: $r"))
      context.become(mergeFailed(repo), discardOld=false)
  }

  def working(requester:ActorRef, failureMessage:String):Receive = {
    case Finished(r:Int) if r == 0 =>
      requester ! Status.Success("")
      context.unbecome()
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"$failureMessage: $r"))
      context.unbecome()
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

  def simpleTask(repo:File, command:Seq[String]): Unit = {
    processor ! Request(repo, command)
    context.become(working(sender(), s"failed: $command"), discardOld=false)
  }

  def ready(org:String, project:String, repo:File):Receive = {
    case Checkout(branch) => simpleTask(repo, s"git checkout $branch")
    case Push(remote) => simpleTask(repo, s"git push $remote")
    case DeleteBranch(branch, remote) => simpleTask(repo, s"git push $remote --delete $branch")
    case AddRemote(remote, path) => simpleTask(repo, s"git remote add $remote $path")
    case Merge(other) =>
      processor ! Request(repo, s"git merge --no-ff origin/$other")
      context.become(merging(repo, sender()), discardOld=false)
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