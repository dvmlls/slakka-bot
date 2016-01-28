import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import cat.dvmlls.WebSocketClient
import cat.dvmlls.slack.web.{Protocol, RTMStart, API}
import java.net.URI
import akka.pattern.pipe

/*
 * https://my.slack.com/services/new/bot
 * https://api.slack.com/web#authentication
 */

/*
 *
 * $ sbt console 2> ~/bot.log
 * scala> Listener.main(Array("<BOT TOKEN>"))
 *  ...
 *  ... logging ...
 *  ...
 * scala> Listener.master ! """{"type":"message","channel":"G06DLTDP0","text":"stfu"}"""
 */

class Unhandler extends Actor with ActorLogging {
  def receive:Receive = {
    case UnhandledMessage(msg, sender, recipient) => log.info(s"not handled: $msg")
  }
}

object Listener extends App {
  val token = args(0)

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  import Protocol._

  val rtmStart = API.createPipeline[RTMStart]("rtm.start")

  class Master extends Actor with ActorLogging {

    val slackClient = system.actorOf(Props { new WebSocketClient(self) })

    rtmStart(API.Request(Map("token" -> token)))
      .map { case RTMStart(url) => new URI(url) }
      .pipeTo(slackClient)

    def receive:Receive = {
      case toSlack:String => slackClient ! toSlack
      case WebSocketClient.Received(fromSlack) => log.info(fromSlack)
    }
  }

  val unhandler = system.actorOf(Props[Unhandler])

  system.eventStream.subscribe(unhandler, classOf[UnhandledMessage])

  val master = system.actorOf(Props[Master])
}