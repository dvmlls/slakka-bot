object Commons extends App {
  import java.util.concurrent.TimeUnit
  import akka.actor.{Props, ActorSystem}
  import akka.util.Timeout
  import scala.util.{Failure, Success}
  import GitActor._
  import GithubActor._

  import akka.pattern.ask

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val h = system.actorOf(Props[GithubActor])
  val p = system.actorOf(Props[StatusPoller])

  val branch = "feature/migrate-and-downgrade"
  val org = "WeConnect"
  val proj = "wework-anywhere"
  val heroku = "wework-anywhere"

  val f = for (
      RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
                     _ <- h ? RepoCloned(repo);
           GotSHA(branchSha) <- (g ? GetSHA(branch, "origin")).mapTo[GotSHA];
           ciSucceeded <- (p ? StatusPoller.Poll(h, branchSha)).map {
                            case s:CISuccess => true
                            case a:Any => println(s"ci failed: $a"); false
                          } ;
                  if ciSucceeded;
                       _ <- g ? Checkout("master");
                       _ <- g ? Merge(branch);
                       _ <- g ? Push("origin");
                       _ <- g ? DeleteBranch(branch, "origin");
                       _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
                       _ <- g ? Push("production")
  ) yield (repo,branchSha,ciSucceeded)

  f.onComplete {
    case Success((repo,sha,ciSucceeded)) =>
      System.out.println(s"success: repo=$repo sha=$sha ciSucceeded=$ciSucceeded")
      system.terminate()
      sys.exit()
    case Failure(ex) =>
      System.err.println("failure: " + ex)
      system.terminate()
      sys.exit()
  }
}

object Spaceman extends App {
  import java.util.concurrent.TimeUnit
  import akka.actor.{Props, ActorSystem}
  import akka.util.Timeout
  import scala.util.{Failure, Success}
  import GitActor._
  import GithubActor._

  import akka.pattern.ask

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.MINUTES)
  import system.dispatcher

  val g = system.actorOf(Props[GitActor])
  val h = system.actorOf(Props[GithubActor])
  val p = system.actorOf(Props[StatusPoller])

  val org = "WeConnect"
  val proj = "spaceman"
  val heroku = "spaceman-production"
  val branch = "bugfix/filter_move_out_ach_on_download"
  val jira = "BILL-391"

  val f = for (
    RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
    _ <- h ? RepoCloned(repo);
    GotSHA(branchSha) <- (g ? GetSHA(branch, "origin")).mapTo[GotSHA];
    ciSucceeded <- (p ? StatusPoller.Poll(h, branchSha)).map {
      case s:CISuccess => true
      case a:Any => println(s"ci failed: $a"); false
    } ;
    if ciSucceeded;
    _ <- g ? Checkout("develop");
    _ <- g ? Merge(branch);
    _ <- g ? Push("origin");
    _ <- g ? DeleteBranch(branch, "origin");
    PullRequested(url) <- (h ? PullRequest(s"$jira: d2m", org, proj, "develop", "master")).mapTo[PullRequested];
    GotSHA(developSha) <- (g ? GetSHA("develop", "origin")).mapTo[GotSHA];
    ciSucceeded2 <- (p ? StatusPoller.Poll(h, developSha)).map {
      case s:CISuccess => true
      case a:Any => println(s"ci failed: $a"); false
    } ;
    if ciSucceeded2;
    _ <- g ? Checkout("master");
    _ <- g ? Merge("develop");
    _ <- g ? Push("origin");
    _ <- g ? AddRemote("production", s"git@heroku.com:$heroku.git");
    _ <- g ? Push("production")
  ) yield (repo,branchSha,ciSucceeded, ciSucceeded2, url)

  f.onComplete {
    case Success((repo,sha,ciSucceeded, ciSucceeded2, url)) =>
      System.out.println(s"success: repo=$repo sha=$sha ciSucceeded=$ciSucceeded ciSucceeded2=$ciSucceeded2 url=$url")
      system.terminate()
      sys.exit()
    case Failure(ex) =>
      System.err.println("failure: " + ex)
      system.terminate()
      sys.exit()
  }
}