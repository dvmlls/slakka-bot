import java.time.Instant

import git.GithubWebProtocol.PRComment
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import spray.json._
import DefaultJsonProtocol._

@RunWith(classOf[JUnitRunner])
class TestGithubParsing extends FunSpec {

  describe("pr comment json") {

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
}
