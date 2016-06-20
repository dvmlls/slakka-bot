package git

import java.util.concurrent.TimeUnit
import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import scala.util.{Failure, Success}
import GitActor._
import GithubWebAPI._
import GithubWebProtocol._
import StatusActor._
import akka.pattern.ask

object GithubFlow extends App {
  val Array(org, proj, heroku, pr) = args

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor], "git")
  val p = system.actorOf(Props[StatusPoller], "poller")

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    (branchName, branchSha, branchResult) <- Autobot.autoMerge(org, proj, pr.toInt, poll)
    if branchResult.isRight;
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    _ <- g ? DeleteBranch(branchName, "origin");
    _ <- g ? Checkout("master");
    _ <- g ? Pull();
    _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
    _ <- g ? Push("production")
  ) yield (repo,(branchName, branchSha, branchResult))

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      system.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      system.shutdown()
      sys.exit(1)
  }
}

object GitFlow extends App {
  val Array(org, proj, heroku, pr, jira) = args

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor], "git")
  val p = system.actorOf(Props[StatusPoller], "poller")

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    (branchName, branchSha, branchResult) <- Autobot.autoMerge(org, proj, pr.toInt, poll)
    if branchResult.isRight;
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
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
      system.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      system.shutdown()
      sys.exit(1)
  }
}

object AutoMergeBranch extends App {
  val Array(org, proj, branch, prTitle) = args

  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)

  val g = system.actorOf(Props[GitActor])
  val p = system.actorOf(Props[StatusPoller])

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    PRCreated(pr) <- createPR(org, proj, prTitle, "", branch, "master");
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    (branchName, branchSha, branchResult) <- Autobot.autoMerge(org, proj, pr, poll);
    _ <- g ? DeleteBranch(branchName, "origin")
  ) yield (branchName, branchSha, branchResult)

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      system.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      system.shutdown()
      sys.exit(1)
  }
}