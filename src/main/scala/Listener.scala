import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import java.net.URI
import spray.json.JsValue

object Listener extends App {
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
  val slakkaBot = "xoxb-4510753551-jjHnAmRXLjh5j60p5VbbOOBe"
//  val method = "rtm.start"
//  val uri = s"https://slack.com/api/$method"

  class Master extends Actor with ActorLogging {

    lazy val slackClient = system.actorOf(Props[WebSocketClient])
    lazy val wrapper = system.actorOf(Props[JSONWrapper])

    var users = Map[String,String]()
    var channels = Map[String,String]()

    val pipeline = WebAPI.createPipeline[RTMStart]("rtm.start")

    wrapper ! (slackClient, self)

    pipeline(Map("token" -> slakkaBot)).map(r => (new URI(r.url), wrapper)).pipeTo(slackClient)

    def receive:Receive = {
      case toSlack:String => slackClient ! toSlack
      case fromSlack:JsValue => println(fromSlack)
    }
  }

  val master = system.actorOf(Props[Master])
}