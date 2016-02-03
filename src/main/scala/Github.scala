import java.io.File
import java.nio.file.Files

import akka.actor.PoisonPill

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
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val h = system.actorOf(Props[GithubActor])

  import GitActor._
  import GithubActor._

  val branch = "feature/github"
  val remote = "origin"
  val target = "master"

  val f = for (
    RepoCloned(repo) <- (g ? CloneRepo("dvmlls", "slakka-bot")).mapTo[RepoCloned];
    x <- (g ? Checkout(target)).mapTo[Succeeded];
    GotSHA(sha) <- (g ? GetSHA(branch, remote)).mapTo[GotSHA];
    y <- (h ? RepoCloned(repo)).mapTo[Succeeded];
    CIStatus(status) <- (h ? CheckCIStatus(sha)).mapTo[CIStatus] ;
    if status == "success" ;
    z <- (g ? Merge(branch)).mapTo[Succeeded];
    w <- (g ? Push(remote)).mapTo[Succeeded];
    u <- (g ? DeleteBranch(branch, remote)).mapTo[Succeeded]
  ) yield (repo,x,y,z,w,u,sha,status)

  f.onComplete {
    case Success((repo,x,y,z,w,u,sha,status)) => System.out.println(s"success: repo=$repo y=$y z=$z, w=$w, u=$u, sha=$sha, status=$status")
    case Failure(ex) => System.err.println("failure: " + ex)
  }

  system.terminate()
}