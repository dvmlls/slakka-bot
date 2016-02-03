import java.io.File
import java.nio.file.Files

import scala.sys.process.{ProcessLogger, Process}

object Github {

  object A {
    val jira = "BILL-379"
    val source = "feature/BILL-379"
    val destination = "develop"
    val heroku = "spaceman-production"
    val proj = "spaceman"
  }

  def cloneRepo(org:String, proj:String):File = {
    val dir = Files.createTempDirectory("repo").toFile

    Process(s"git clone git@github.com:$org/$proj.git", cwd=dir).!

    new File(dir, proj)
  }

  val org = "WeConnect"

  object B {
    val jira = "MONEY-23"
    val proj = "spacemoney"
    val branch = "feature/remove-split"
    val heroku = "spacemoney"
  }

  def getSha(branch:String, cwd:File): String = {
    Process(s"git log origin $branch", cwd=cwd).!!.split('\n').head.split(' ') match { case Array(_, sha) => sha }
  }

  def getStatus(sha:String, cwd:File):String = Process(s"hub ci-status $sha", cwd=cwd).!!.trim()

  def d2m(jira:String, org:String, proj:String): Unit = {
    Process(Seq("hub", "pull-request", "-m", s"$jira: d2m", "-b", s"$org/$proj:master", "-h", s"$org/$proj:develop")).!!.trim()
  }

  def merge(source:String, destination:String, repo:File):Option[Int] = {

    Process(s"git checkout $destination", cwd=repo).!!.trim()

    var outs = List[String]()
    var errs = List[String]()

    val ret = Process(s"git merge --no-ff origin/$source", cwd=repo) ! ProcessLogger(out => outs = out :: outs, err => errs = err :: errs)

    if (ret != 0) return Some(ret)

    Process(s"git push origin $destination", cwd=repo).!

    None
  }

  def abortMerge(repo:File): Unit = Process("git merge --abort", cwd=repo).!

  def deleteBranch(branchName:String, repo:File): Unit = Process(s"git push origin --delete $branchName", cwd=repo).!!

  def deploy(heroku:String, repo:File): Unit = {
    Process(s"git remote add production git@heroku.com:$heroku.git", cwd=repo).!
    Process(s"git checkout master", cwd=repo).!!.trim()
    Process(s"git push production", cwd=repo).!!.trim()
  }
}

object Test extends App {
  import java.util.concurrent.TimeUnit
  import akka.actor.{Props, ActorSystem}
  import akka.util.Timeout
  import scala.util.{Failure, Success}

  import akka.pattern.ask

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val h = system.actorOf(Props[GithubActor])
  val p = system.actorOf(Props[StatusPoller])

  import GitActor._
  import GithubActor._

  val branch = "feature/status_polling"
  val remote = "origin"
  val target = "master"
  val org = "dvmlls"
  val proj = "slakka-bot"

  val f = for (
      RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
                     _ <- h ? RepoCloned(repo);
    PullRequested(url) <- (h ? PullRequest("status polling", org, proj, branch, target)).mapTo[PullRequested];
                     _ <- g ? Checkout(target);
           GotSHA(sha) <- (g ? GetSHA(branch, remote)).mapTo[GotSHA];
           ciSucceeded <- (p ? StatusPoller.Poll(h, sha)).map {
                            case s:CISuccess => true
                            case _ => println("ci failed"); false
                          } ;
                  if ciSucceeded;
                     _ <- g ? Merge(branch);
                     _ <- g ? Push(remote);
                     _ <- g ? DeleteBranch(branch, remote)
  ) yield (repo,sha,ciSucceeded, url)

  f.onComplete {
    case Success((repo,sha,ciSucceeded, url)) => System.out.println(s"success: repo=$repo sha=$sha ciSucceeded=$ciSucceeded url=$url")
    case Failure(ex) => System.err.println("failure: " + ex)
  }

  system.terminate()
}