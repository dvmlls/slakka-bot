import akka.actor.ActorSystem

import scala.concurrent.{ExecutionContext, Future}

object Commons extends App {
  import java.util.concurrent.TimeUnit
  import akka.actor.{Props, ActorSystem}
  import akka.util.Timeout
  import scala.util.{Failure, Success}
  import GitActor._
  import StatusActor._

  import akka.pattern.ask

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val p = system.actorOf(Props[StatusPoller])

  val org = "WeConnect"
  val proj = "wework-anywhere"
  val heroku = "wework-anywhere"
  val pr = 1234

  val f = for (
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus];
    (branchName, branchSha, branchResult) <- Autobot.autoMerge(org, proj, pr, poll)
    if branchResult.isRight;
    _ <- g ? DeleteBranch(branchName, "origin");
    _ <- g ? Checkout("master");
    _ <- g ? Pull();
    _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
    _ <- g ? Push("production")
  ) yield (repo,(branchName, branchSha, branchResult))

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      system.terminate()
      sys.exit()
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      system.terminate()
      sys.exit()
  }
}

object Autobot {
  import StatusActor._
  import GithubWebAPI._
  import GithubWebProtocol._

  def autoMerge(org:String, proj:String, pr:Int, poll:String => Future[CIStatus])
               (implicit sys:ActorSystem, ctx:ExecutionContext) = {
    for (
      (branchName, sha) <- getPR(org, proj, pr).flatMap {
        case PR(_, "open", PRHead(branchName, sha), _, _) => Future { (branchName, sha) }
        case PR(_, failed, _, _, _) => Future.failed(new Exception(s"pr not open: $failed"))
      };
      _ <- poll(sha).flatMap {
        case s:CISuccess => Future { true }
        case a:Any => Future.failed(new Exception(s"polling for CI status failed: $a"))
      };
      result <- mergePR(org, proj, pr, sha)
    ) yield (branchName, sha, result)
  }
}

object Spaceman extends App {
  import java.util.concurrent.TimeUnit
  import akka.actor.{Props, ActorSystem}
  import akka.util.Timeout
  import scala.util.{Failure, Success}

  import GitActor._
  import GithubWebAPI._
  import GithubWebProtocol._
  import StatusActor._

  import akka.pattern.ask

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val p = system.actorOf(Props[StatusPoller])

  val org = "WeConnect"
  val proj = "spaceman"
  val heroku = "spaceman-production"
  val prNumber = 1234
  val jira = "BILL-391"

  val f = for (
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus];
    (branchName, branchSha, branchResult) <- Autobot.autoMerge(org, proj, prNumber, poll)
    if branchResult.isRight;
    _ <- g ? DeleteBranch(branchName, "origin");
    PRCreated(d2m) <- createPR(org, proj, s"$jira: d2m", "", "develop", "master");
    (_, masterSha, masterResult) <- Autobot.autoMerge(org, proj, d2m, poll)
    if masterResult.isRight;
    _ <- g ? Checkout("master");
    _ <- g ? Pull();
    _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
    _ <- g ? Push("production")
  ) yield (repo, (branchName, branchSha, branchResult), (d2m, masterSha, masterResult))

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      system.terminate()
      sys.exit()
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      system.terminate()
      sys.exit()
  }
}