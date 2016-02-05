import java.io.File
import java.nio.file.Files

import ProcessActor._
import akka.actor._

import scala.util.{Failure, Success, Try}

object GitActor {
  case class CloneRepo(org:String, project:String)
  case class RepoCloned(repo:File)
  case class Checkout(branch:String)
  case class Push(remote:String)
  case class Pull()
  case class DeleteBranch(branch:String, remote:String)
  case class AddRemote(name:String, path:String)
}

class GitActor extends Actor with ActorLogging {
  import GitActor._

  val processor = context.actorOf(Props[ProcessActor])

  def working(requester:ActorRef, failureMessage:String):Receive = { log.debug("state -> working"); {
    case Finished(r:Int) if r == 0 =>
      requester ! Status.Success("")
      context.unbecome()
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"$failureMessage: $r"))
      context.unbecome()
  }}

  def simpleTask(repo:File, command:Seq[String]): Unit = {
    processor ! Request(repo, command)
    context.become(working(sender(), s"failed: $command"), discardOld=false)
  }

  def ready(org:String, project:String, repo:File):Receive = { log.debug("state -> ready"); {
    case Checkout(branch) => simpleTask(repo, s"git checkout $branch")
    case Push(remote) => simpleTask(repo, s"git push $remote")
    case DeleteBranch(branch, remote) => simpleTask(repo, s"git push $remote --delete $branch")
    case AddRemote(remote, path) => simpleTask(repo, s"git remote add $remote $path")
    case Pull() => simpleTask(repo, "git pull")
  }}

  def cloning(org:String, project:String, requester:ActorRef, repo:File):Receive = { log.debug("state -> cloning"); {
    case Finished(r:Int) if r == 0 =>
      requester ! RepoCloned(repo)
      context.become(ready(org, project, repo))
    case Finished(r:Int) if r != 0 =>
      requester ! Status.Failure(new Exception(s"clone failed: $r"))
      context.unbecome()
  }}

  def empty:Receive = { log.debug("state -> empty"); {
    case CloneRepo(org, project) =>
      Try { Files.createTempDirectory("repo").toFile } match {
        case Success(parent) =>
          processor ! Request(parent, s"git clone git@github.com:$org/$project.git")
          context.become(cloning(org, project, sender(), new File(parent, project)), discardOld = false)
        case Failure(ex) => sender() ! Status.Failure(ex)
      }
  }}

  def receive = empty
}