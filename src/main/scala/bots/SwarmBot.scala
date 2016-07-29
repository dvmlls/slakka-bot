package bots

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe

import git.GithubWebAPI
import slack.ChannelService.ChannelId
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.SlackWebAPI
import slack.SlackWebProtocol.{EmojiList, RTMSelf}

object SwarmBot {
  val SwarmPattern = """.*(unleash|release) the ([a-z0-9_-]+)s""".r
}

class SwarmBot(slack:ActorRef, api:GithubWebAPI)(implicit val t:SlackWebAPI.Token) extends Actor with ActorLogging {
  implicit val ec = context.dispatcher
  implicit val sys = context.system
  import SwarmBot.SwarmPattern

  def connected(emojis:Set[String]):Receive = { log.info("state -> connected")

    log.info("emoji: " + emojis.toList.sorted.mkString(", "))

    {
      case MessageReceived(ChannelId(channelId), _, SwarmPattern(_, emoji), Some(ts)) if emojis.contains(emoji) =>
        slack ! SendMessage(channelId, s":$emoji: " * 150)
    }
  }

  def loading(id:String, name:String):Receive = { log.info("state -> loading")

    val pipeline = SlackWebAPI.createPipeline[EmojiList]("emoji.list")

    case class Emojis(emoji:Set[String])

    val Pattern = """(.+)[.]png""".r

    val f = for (
      custom <- pipeline(Map());
      built_ins <- api.listContents("WebpageFX", "emoji-cheat-sheet.com", "public/graphics/emojis")
    ) yield {

      val b = built_ins.flatMap(_.name match {
        case Pattern(s) => Some(s)
        case _ => None
      })

      Emojis(custom.emoji.keySet ++ b.toSet)
    }

    f.pipeTo(self)

    {
      case Emojis(emojis) => context.become(connected(emojis))
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(loading(id, name))
  }}
}