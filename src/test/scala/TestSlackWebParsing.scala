import org.junit.runner.RunWith
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import slack.SlackWebProtocol.UserList
import spray.json.JsonParser

@RunWith(classOf[JUnitRunner])
class TestSlackWebParsing extends FunSpec {
  describe ("users list json") {
    lazy val stream = getClass.getResourceAsStream("/slack_web_api/users_list.json")
    lazy val lines = scala.io.Source.fromInputStream(stream).getLines.mkString
    lazy val parsed = JsonParser(lines)

    it ("is valid json") { assert(parsed !== null) }

    lazy val members = parsed.convertTo[UserList].members

    it ("is a list of issue comments") { assert(members.length === 1) }

    lazy val member = members.head

    it ("has a name") { assert(member.name === "bobby") }
    it ("has an email") { assert(member.profile.email === Some("bobby@slack.com")) }
    it ("has a full name") { assert(member.real_name === Some("Bobby Tables")) }
  }
}
