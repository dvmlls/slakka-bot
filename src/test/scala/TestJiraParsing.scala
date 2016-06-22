import jira.JiraWebProtocol.Issue
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import slack.SlackChatActor
import spray.json._
import DefaultJsonProtocol._

import jira.JIRA.{TicketPattern, CommandPattern}

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

  describe("recognizing a JIRA ticket") {
    val userId = "U0K3W1BK3"
    val Mention = SlackChatActor.mentionPattern(userId)
    val ticket = "DATA-411"

    it ("should be able to find tickets") {
      s"<@$userId>: deploy $ticket" match {
        case Mention(TicketPattern(s)) => assert(s === ticket)
        case any:Any => fail(any)
      }
    }

    it ("should be able to handle stuff") {
      s"<@$userId>: deploy $ticket" match {
        case Mention(CommandPattern(command, t)) =>
          assert(command === "deploy")
          assert(t === ticket)
        case any:Any => fail(any)
      }
    }
  }
}