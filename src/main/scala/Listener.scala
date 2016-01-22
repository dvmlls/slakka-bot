import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import java.net.URI
import spray.json.JsValue

/*
 *
 * $ sbt console 2> ~/bot.log
 * scala> Listener.main(Array("<BOT TOKEN>"))
 *  ...
 *  ... logging ...
 *  ...
 * scala> Listener.master ! "{'type':'message','channel':'G06DLTDP0','text':'stfu'}"
 *
 */

object Listener extends App {

  val token = args(0)

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  case class RTMStart(url:String)
  object RTMStartProtocol extends DefaultJsonProtocol {
    implicit val rtmStartFormat = jsonFormat1(RTMStart)
  }
  import RTMStartProtocol._
  import spray.httpx.SprayJsonSupport._

  /*
   * https://my.slack.com/services/new/bot
   * https://api.slack.com/web#authentication
   */

  class Master extends Actor with ActorLogging {

    lazy val slackClient = system.actorOf(Props[WebSocketClient])
    lazy val wrapper = system.actorOf(Props[JSONWrapper])

    var users = Map[String,String]()
    var channels = Map[String,String]()

    val pipeline = WebAPI.createPipeline[RTMStart]("rtm.start")

    wrapper ! (slackClient, self)

    pipeline(Map("token" -> token)).map(r => (new URI(r.url), wrapper)).pipeTo(slackClient)

    def receive:Receive = {
      case toSlack:String => slackClient ! toSlack
      case fromSlack:JsValue => log.info(fromSlack.toString)
    }
  }

  val master = system.actorOf(Props[Master])
}