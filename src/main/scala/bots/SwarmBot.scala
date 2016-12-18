package bots

import akka.actor.{Actor, ActorLogging, ActorRef, Status}
import akka.pattern.pipe
import git.GithubWebAPI
import slack.ChannelService.ChannelId
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.SlackWebAPI
import slack.SlackWebProtocol.{EmojiList, RTMSelf}

import scala.util.matching.Regex

object SwarmBot {
  val SwarmPattern: Regex = """(?i).*(unleash|release) the ([a-z0-9_-]+)s""".r
}

class SwarmBot(slack:ActorRef, api:GithubWebAPI)(implicit val t:SlackWebAPI.Token) extends Actor with ActorLogging {
  private[this] implicit val ec = context.dispatcher
  private[this] implicit val sys = context.system
  import SwarmBot.SwarmPattern

  def connected(emojis:Set[String]):Receive = { log.info("state -> connected \n emoji: " + emojis.toList.sorted.mkString(", "))

    {
      case MessageReceived(ChannelId(channelId), _, SwarmPattern(_, emoji), _) if emojis.contains(emoji.toLowerCase) =>
        slack ! SendMessage(channelId, s":${emoji.toLowerCase()}: " * 100)
    }
  }

  def loading(id:String, name:String):Receive = { log.info("state -> loading")

    val pipeline = SlackWebAPI.createPipeline[EmojiList]("emoji.list")

    case class Emojis(emoji:Set[String])

    val Pattern = """(.+)[.]png""".r

    val f = for (
      custom <- pipeline(Map());
      builtIns <- api.listContents("WebpageFX", "emoji-cheat-sheet.com", "public/graphics/emojis")
    ) yield {

      val b = builtIns.flatMap { _.name match { // collect?
        case Pattern(s) => Some(s)
        case _ => None
      }}

      Emojis(custom.emoji.keySet ++ b.toSet)
    }

    f.pipeTo(self)

    {
      case Emojis(emojis) => context.become(connected(emojis))
      case Status.Failure(ex) =>
        /*
         *  TODO: if this happens and the supervision strategy is RESTART, need a way to re-send this actor his
         *        startup parameters so he doesn't get stuck in the "disconnected" state
         */
        throw new Exception("error starting up - have you specified your slack token?", ex)
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(loading(id, name))
  }}
}