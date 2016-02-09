package slack

import akka.actor.{ActorLogging, Actor}
import slack.SlackWebAPI.Request
import slack.SlackWebProtocol.{IMChannel, IMOpen}
import akka.pattern.pipe

import scala.collection.{immutable, mutable}

object IMService {
  case class OpenIM(userId:String)
  case class IMOpened(userId:String, channelId:String)
}

class IMService extends Actor with ActorLogging {
  import IMService._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val imOpen = SlackWebAPI.createPipeline[IMOpen]("im.open")
  //  val imList = SlackWebAPI.createPipeline[IMList]("im.list")

  def receive = {

    val id2channel = mutable.Map[String, String]()

    {
      case OpenIM(userId) =>
        id2channel.get(userId) match {
          case Some(channel) => sender() ! IMOpened(userId, channel)
          case None =>
            val future = imOpen(Request(immutable.Map("user" -> userId)))
              .map { case IMOpen(IMChannel(channelId)) => IMOpened(userId, channelId) }

            future.pipeTo(self)
            future.pipeTo(sender())
        }
      case IMOpened(userId, channelId) => id2channel += userId -> channelId
    }
  }
}
