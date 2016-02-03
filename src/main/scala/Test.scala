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

  val branch = "feature/less_git_log"
  val remote = "origin"
  val target = "master"
  val org = "dvmlls"
  val proj = "slakka-bot"
  val comment = "less git log"

  val f = for (
      RepoCloned(repo) <- (g ? CloneRepo(org, proj)).mapTo[RepoCloned];
                     _ <- h ? RepoCloned(repo);
    PullRequested(url) <- (h ? PullRequest(comment, org, proj, branch, target)).mapTo[PullRequested];
                     _ <- g ? Checkout(target);
           GotSHA(sha) <- (g ? GetSHA(branch, remote)).mapTo[GotSHA];
           ciSucceeded <- (p ? StatusPoller.Poll(h, sha)).map {
                            case s:CISuccess => true
                            case a:Any => println(s"ci failed: $a"); false
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