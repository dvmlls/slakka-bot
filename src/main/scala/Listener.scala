import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.pipe
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.json.DefaultJsonProtocol
import spray.httpx.SprayJsonSupport._

import scala.concurrent.Future

object Listener extends App {
  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  case class RTMStart(url:String)
  object RTMStartProtocol extends DefaultJsonProtocol {
    implicit val rtmStartFormat = jsonFormat1(RTMStart)
  }
  import RTMStartProtocol._

  /*
   * https://my.slack.com/services/new/bot
   * https://api.slack.com/web#authentication
   */
  val slakkaBot = "xoxb-4510753551-jjHnAmRXLjh5j60p5VbbOOBe"
//  val dvmllsBearerToken = "xoxp-4439240800-4439240824-4484187659-3e4c68"
//  val oauthToken = "xoxp-4439240800-4439240824-4462184742-27352d"
  val method = "rtm.start"
  val uri = s"https://slack.com/api/$method"

  val pipeline: HttpRequest => Future[RTMStart] = sendReceive ~> unmarshal[RTMStart]

  class Master extends Actor with ActorLogging {

    lazy val client = system.actorOf(Props(new WebSocketClient(self)))

    val post = Post(uri, FormData(Seq("token" -> slakkaBot)))
    pipeline(post).map(_.url).pipeTo(client)

    def receive:Receive = {
      case message:String => if (sender() != client) client ! message
    }
  }

  val master = system.actorOf(Props[Master])
}