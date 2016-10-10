import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import slack.MessageMatcher
import slack.SlackChatActor.MessageReceived
import spray.json.JsonParser
import util.WebSocketClient.Received
import slack.SlackChatActor

@RunWith(classOf[JUnitRunner])
class TestSlackRTParsing extends FunSpec {

  def parse(s:String):Received = Received(JsonParser(s))

  describe("presence change events") {
    val s = """{"type":"presence_change","user":"U03QBUQFE","presence":"active"}"""
    it("isn't a message") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message, _))) => println(message)
        }
      }
    }
  }

  describe("reconnect url events") {
    val s = """{"type":"reconnect_url","url":"wss://ms509.slack-msgs.com/websocket/ewsgoHNdovsc_dtFT4zyLSoZX9lCEB8iuWNCZi5bE8l67Bq6EyN6my4XzzeyPFDGlQBdltWJXF3piMpmbypg1fnxZnyeT8rgE9FVxTp2yCXNVHvf-NXJSEPoU4UxmF5vP5Aa20NIpD7Eyk_pF5AweAJU7mxKjqY15nu7QNdvsog="}"""
    it("isn't a message") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message, _))) => println(message)
        }
      }
    }
  }

  describe("instant messages") {
    val s = """{"channel":"D0K3XHE3Y","ts":"1456282553.000029","text":"hi","team":"T03KB9Y6Y","type":"message","user":"U06DF12SU"}"""
    it ("are totally messages") {
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, message, _))) => message === "hi"
      }
    }
  }

  describe("mentions") {
    val s = """{"channel":"D0K3XHE3Y","ts":"1456282618.000030","text":"hello <@U0K3W1BK3>  how are you doing today?","team":"T03KB9Y6Y","type":"message","user":"U06DF12SU"}"""
    it ("are totally messages") {
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, message, _))) => assert(message.length > 0)
      }
    }

    it ("also mention-matches my user id") {
      val myUserId = "U0K3W1BK3"
      val Mention = SlackChatActor.mentionPattern(myUserId)
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, Mention(message), _))) => message.trim === "how are you doing today?"
      }
    }

    it ("doesn't mention-match someone else's user id") {
      val myUserId = "U0K4W1BK3"
      val Mention = SlackChatActor.mentionPattern(myUserId)
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, Mention(message), _))) => println(message)
        }
      }
    }
  }

  describe("deleted messages") {
    val s = """{"channel":"D0K3XHE3Y","subtype":"message_deleted","ts":"1456282696.000038","previous_message":{"type":"message","user":"U06DF12SU","text":"hi2","ts":"1456282688.000036"},"hidden":true,"deleted_ts":"1456282688.000036","event_ts":"1456282696.395419","type":"message"}"""

    it("aren't actually messages") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message, _))) => println(message)
        }
      }
    }
  }

  describe("group joined events") {
    val s = """{"type":"group_joined","channel":{"name":"dvtest","is_mpim":false,"last_read":"1456335577.000003","is_open":true,"creator":"U06DF12SU","is_group":true,"purpose":{"value":"","creator":"","last_set":0},"id":"G0NSL6F5H","unread_count":0,"unread_count_display":0,"members":["U06DF12SU","U0K3W1BK3"],"topic":{"value":"","creator":"","last_set":0},"latest":{"type":"message","user":"U06DF12SU","text":"<@U0K3W1BK3>: hi?","ts":"1456335577.000003"},"is_archived":false,"created":1456335572}}"""

    it("aren't messages") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message, _))) => println(message)
        }
      }
    }
  }
}