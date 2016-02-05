import akka.actor.ActorSystem
import GithubWebAPI._
import GithubWebProtocol._
import StatusActor._
import scala.concurrent.{ExecutionContext, Future}

object Autobot {
  def autoMerge(org:String, proj:String, pr:Int, poll:String => Future[CIStatus])
               (implicit sys:ActorSystem, ctx:ExecutionContext) = {

    val pollForFailures:String => Future[CIStatus] = (sha:String) => poll(sha).flatMap {
      case f:CIFailure => Future.failed(new Exception(s"ci failed - retry?"))
      case s:CIStatus => Future { s }
    }

    def retry(sha:String) = for (
      _ <- comment(org, proj, pr, "retest this please");
      _ <- Future { Thread.sleep(30 * 1000) };
      s <- pollForFailures(sha)
    ) yield s

    val retryPoll = (sha:String) => pollForFailures(sha)
      .recoverWith { case _ =>  retry(sha) }
      .recoverWith { case _ =>  retry(sha) }
      .recoverWith { case _ =>  retry(sha) }
      .recoverWith { case _ =>  retry(sha) }
      .recoverWith { case _ =>  retry(sha) }

    for (
      (branchName, sha) <- getPR(org, proj, pr).flatMap {
        case PR(_, "open", PRHead(branchName, sha), _, _) => Future { (branchName, sha) }
        case PR(_, failed, _, _, _) => Future.failed(new Exception(s"pr not open: $failed"))
      };
      _ <- retryPoll(sha).flatMap {
        case s:CISuccess => Future { true }
        case a:Any => Future.failed(new Exception(s"polling for CI status failed: $a"))
      };
      result <- mergePR(org, proj, pr, sha)
    ) yield (branchName, sha, result)
  }
}
