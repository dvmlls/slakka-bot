import java.util.concurrent.TimeUnit

import akka.actor._
import akka.pattern.pipe
import akka.util.Timeout
import spray.http.HttpRequest
import spray.json.{JsonParser, DefaultJsonProtocol, JsValue}
import java.net.URI
import akka.pattern.{pipe, ask}

import scala.concurrent.Future
import spray.httpx.SprayJsonSupport._


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
 * scala> Listener.master ! """ {"type":"message","channel":"G06DLTDP0","text":"stfu"} """
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

  case class RTMStart(url:String)
  val rtmStart = {
    object RTMStartProtocol extends DefaultJsonProtocol {
      implicit val rtmStartFormat = jsonFormat1(RTMStart)
    }
    import RTMStartProtocol._

    SlackWebAPI.createPipeline[RTMStart]("rtm.start", j => j.convertTo[RTMStart])
  }

  class Master extends Actor with ActorLogging {

    val slackClient = system.actorOf(Props { new WebSocketClient(self) })

    rtmStart(SlackWebAPI.Request(Map("token" -> token)))
      .flatMap {
        case Right(RTMStart(url)) => Future(new URI(url))
        case Left(error) => Future.
      }
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