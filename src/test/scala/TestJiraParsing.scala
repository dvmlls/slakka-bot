import jira.JiraWebProtocol.Issue
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import spray.json._
import DefaultJsonProtocol._

@RunWith(classOf[JUnitRunner])
class TestJiraParsing extends FunSpec {

  describe("PR comment json") {

    lazy val stream = getClass.getResourceAsStream("/jira_web_api/issue.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it("is valid json") { assert(parsed !== null) }

    lazy val issue = parsed.convertTo[Issue]

    it ("has a status") { assert(issue.fields.status.name === "Ready to Deploy") }
    it ("has a summary") { assert(issue.fields.summary === "blah") }
  }
}
