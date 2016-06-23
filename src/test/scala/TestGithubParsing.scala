import java.time.Instant

import git.GithubWebProtocol.{PRCommit, PR, PRComment}
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import spray.json._
import DefaultJsonProtocol._

@RunWith(classOf[JUnitRunner])
class TestGithubParsing extends FunSpec {

  describe("PR comment json") {

    lazy val stream = getClass.getResourceAsStream("/github_web_api/pr_comments.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it("is valid json") { assert(parsed !== null) }

    lazy val list = parsed.convertTo[List[PRComment]]

    it ("is a list of PR comments") { assert(list.length === 1) }

    lazy val comment = list.head

    it ("has a body") { assert(comment.body === "Great stuff") }
    it ("has an updated at") { assert(comment.updated_at === Instant.parse("2011-04-14T16:00:49Z")) }
    it ("has a user") { assert(comment.user.login === "octocat") }
    it ("has a commit id") { assert(comment.commit_id === "6dcb09b5b57875f334f61aebed695e2e4193db5e") }
  }

  describe("PR json") {
    lazy val stream = getClass.getResourceAsStream("/github_web_api/pr.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it ("is valid json") { assert(parsed !== null) }

    lazy val pr = parsed.convertTo[PR]

    it ("is a pull request") { assert(pr !== null) }
    it ("has a state") { assert(pr.state === "open") }
  }

  describe("PR commits json") {
    lazy val stream = getClass.getResourceAsStream("/github_web_api/pr_commits.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it ("is valid json") { assert(parsed !== null) }

    lazy val commits = parsed.convertTo[List[PRCommit]]

    it ("is a list of commits") { assert(commits.length === 1) }

    lazy val commit = commits.head

    it ("has a committer") { assert(commit.committer.login === "octocat") }
    it ("has a date") { assert(commit.commit.committer.date === Instant.parse("2011-04-14T16:00:49Z")) }
    it ("has a sha") { assert(commit.sha === "6dcb09b5b57875f334f61aebed695e2e4193db5e") }
  }

  describe("list PRs json") {
    lazy val stream = getClass.getResourceAsStream("/github_web_api/list_prs.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it ("is valid json") { assert(parsed !== null) }

    lazy val prs = parsed.convertTo[List[PR]]

    it ("has some PRs in it") { assert(prs.length === 1)}

    lazy val pr = prs.head

    it ("has an id") { assert(pr.number === 1347) }
    it ("has a name") { assert(pr.title === "new-feature") }
  }
}
