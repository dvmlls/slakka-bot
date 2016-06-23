import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout

import scala.util.{Success, Failure}

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

import git.GithubWebAPI._

val f = listPulls("WeConnect", "spacemoney")

f.onSuccess {
  case l => println("pulls: " + l)
}

f.onFailure {
  case ex => println("pulls failed: " + ex)
}

val f3 = for (
  commits <- getCommits("WeConnect", "spacemoney", 639) ;
  comments <- getIssueComments("WeConnect", "spacemoney", 639) ;
  pr <- getPR("WeConnect", "spacemoney", 639)
) yield (comments, pr, commits)

val unicodeThumbsUp = "\uD83D\uDC4D"

f3.onComplete {
  case Success((comments, pr, commits)) =>
    val committers = commits.map(_.committer.login).toSet
    val lastCommit = commits.sortBy(_.commit.committer.date).last
    val thumbsFrom = comments
      .filter(comment => !committers.contains(comment.user.login))
      .filter(comment => comment.body.contains(unicodeThumbsUp))
      .filter(comment => comment.updated_at.isAfter(lastCommit.commit.committer.date))
      .map(_.user.login)
      .toSet

    println(s"pr: sha=${ pr.head.sha } ref=${ pr.head.ref }")
    comments.foreach(c => {
      println(s"comment: user=${ c.user.login } id=${ c.id } body=${ c.body } ts=${ c.updated_at }")
    })

    commits.foreach(c => {
      println(s"commit: user=${ c.committer.login } ts=${ c.commit.committer.date } sha=${ c.sha }")
    })

    println("authors: " + committers)
    println("last commit: " + lastCommit)
    println("thumbs: " + thumbsFrom)

    system.shutdown()
    sys.exit()

  case Failure(ex) =>
    println("comments failed: " + ex)
    system.shutdown()
    sys.exit()
}


