import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest._
import org.scalatest.junit.JUnitRunner
import slack.MessageMatcher
import slack.SlackChatActor.MessageReceived
import spray.json.{JsValue, JsonParser}

import org.scalatest.prop.Checkers
import util.WebSocketClient.Received
import slack.SlackChatActor

@RunWith(classOf[JUnitRunner])
class TestParseSlack extends FunSpec {

  def parse(s:String):Received = Received(JsonParser(s))

  describe("presence change events") {
    val s = """{"type":"presence_change","user":"U03QBUQFE","presence":"active"}"""
    it("isn't a message") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message))) => println(message)
        }
      }
    }
  }

  describe("reconnect url events") {
    val s = """{"type":"reconnect_url","url":"wss://ms509.slack-msgs.com/websocket/ewsgoHNdovsc_dtFT4zyLSoZX9lCEB8iuWNCZi5bE8l67Bq6EyN6my4XzzeyPFDGlQBdltWJXF3piMpmbypg1fnxZnyeT8rgE9FVxTp2yCXNVHvf-NXJSEPoU4UxmF5vP5Aa20NIpD7Eyk_pF5AweAJU7mxKjqY15nu7QNdvsog="}"""
    it("isn't a message") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message))) => println(message)
        }
      }
    }
  }

  describe("instant messages") {
    val s = """{"channel":"D0K3XHE3Y","ts":"1456282553.000029","text":"hi","team":"T03KB9Y6Y","type":"message","user":"U06DF12SU"}"""
    it ("are totally messages") {
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, message))) => message === "hi"
      }
    }
  }

  describe("mentions") {
    val s = """{"channel":"D0K3XHE3Y","ts":"1456282618.000030","text":"hello <@U0K3W1BK3>  how are you doing today?","team":"T03KB9Y6Y","type":"message","user":"U06DF12SU"}"""
    it ("are totally messages") {
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, message))) => assert(message.length > 0)
      }
    }

    it ("also mention-matches my user id") {
      val myUserId = "U0K3W1BK3"
      val Mention = SlackChatActor.mentionPattern(myUserId)
      parse(s) match {
        case Received(MessageMatcher(MessageReceived(_, _, Mention(message)))) => message.trim === "how are you doing today?"
      }
    }

    it ("doesn't mention-match someone else's user id") {
      val myUserId = "U0K4W1BK3"
      val Mention = SlackChatActor.mentionPattern(myUserId)
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, Mention(message)))) => println(message)
        }
      }
    }
  }

  describe("deleted messages") {
    val s = """{"channel":"D0K3XHE3Y","subtype":"message_deleted","ts":"1456282696.000038","previous_message":{"type":"message","user":"U06DF12SU","text":"hi2","ts":"1456282688.000036"},"hidden":true,"deleted_ts":"1456282688.000036","event_ts":"1456282696.395419","type":"message"}"""

    it("aren't actually message") {
      intercept[MatchError] {
        parse(s) match {
          case Received(MessageMatcher(MessageReceived(_, _, message))) => println(message)
        }
      }
    }
  }
}