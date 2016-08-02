package git

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask

import scala.util.{Failure, Success}

import GitActor._
import GithubWebAPI._
import GithubWebProtocol._
import StatusActor._

object GithubFlow extends App {
  val Array(org, proj, heroku, pr) = args

  implicit val actorSystem = ActorSystem()
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)
  implicit val ec = actorSystem.dispatcher

  object API extends Autobot {
    override implicit def system: ActorSystem = actorSystem
    override def token: Token = Token(sys.env("GITHUB_TOKEN"))
  }

  val g = actorSystem.actorOf(Props[GitActor], "git")
  val p = actorSystem.actorOf(Props { new StatusPoller(API) }, "poller")

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    (branchName, branchSha, branchResult) <- API.autoMerge(org, proj, pr.toInt, poll)
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
      actorSystem.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      actorSystem.shutdown()
      sys.exit(1)
  }
}

object GitFlow extends App {
  val Array(org, proj, heroku, pr, jira) = args

  implicit val actorSystem = ActorSystem()
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)
  implicit val ec = actorSystem.dispatcher

  object API extends Autobot {
    override implicit def system: ActorSystem = actorSystem
    override def token: Token = Token(sys.env("GITHUB_TOKEN"))
  }

  val g = actorSystem.actorOf(Props[GitActor], "git")
  val p = actorSystem.actorOf(Props { new StatusPoller(API) }, "poller")

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    (branchName, branchSha, branchResult) <- API.autoMerge(org, proj, pr.toInt, poll)
    if branchResult.isRight;
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    _ <- g ? DeleteBranch(branchName, "origin");
    PRCreated(d2m) <- API.createPR(org, proj, s"$jira: d2m", "", "develop", "master");
    (_, masterSha, masterResult) <- API.autoMerge(org, proj, d2m, poll)
    if masterResult.isRight;
    _ <- g ? Checkout("master");
    _ <- g ? Pull();
    _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
    _ <- g ? Push("production")
  ) yield (repo, (branchName, branchSha, branchResult), (d2m, masterSha, masterResult))

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      actorSystem.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      actorSystem.shutdown()
      sys.exit(1)
  }
}

object AutoMergeBranch extends App {
  val Array(org, proj, branch, prTitle) = args

  implicit val actorSystem = ActorSystem()
  implicit val timeout = new Timeout(15, TimeUnit.MINUTES)
  implicit val ec = actorSystem.dispatcher

  object API extends Autobot {
    override implicit def system: ActorSystem = actorSystem
    override def token: Token = Token(sys.env("GITHUB_TOKEN"))
  }

  val g = actorSystem.actorOf(Props[GitActor])
  val p = actorSystem.actorOf(Props { new StatusPoller(API) }, "poller")

  val poll = (sha:String) => (p ? CheckCIStatus(org, proj, sha)).mapTo[CIStatus]

  val f = for (
    PRCreated(pr) <- API.createPR(org, proj, prTitle, "", branch, "master");
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    (branchName, branchSha, branchResult) <- API.autoMerge(org, proj, pr, poll);
    _ <- g ? DeleteBranch(branchName, "origin")
  ) yield (branchName, branchSha, branchResult)

  f.onComplete {
    case Success(s) =>
      System.out.println(s"success: $s")
      actorSystem.shutdown()
      sys.exit(0)
    case Failure(ex) =>
      System.err.println(s"failure: $ex")
      actorSystem.shutdown()
      sys.exit(1)
  }
}