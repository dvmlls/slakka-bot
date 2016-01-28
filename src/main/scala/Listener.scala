import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import cat.dvmlls.{Unhandler, WebSocketClient}
import cat.dvmlls.slack.web._
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

object Listener extends App {
  val token = args(0)

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  import Protocol._

  val blankRequest = API.Request(Map("token" -> token))
  val rtmStart = API.createPipeline[RTMStart]("rtm.start")
  val channels = API.createPipeline[ChannelList]("channels.list")
  val users = API.createPipeline[UserList]("users.list")

  class Master extends Actor with ActorLogging {

    val slackClient = system.actorOf(Props { new WebSocketClient(self) })

    rtmStart(blankRequest)
      .map { case RTMStart(url) => new URI(url) }
      .pipeTo(slackClient)

    def receive:Receive = {
      case toSlack:String => slackClient ! toSlack
      case WebSocketClient.Received(fromSlack) => log.info(fromSlack)
    }
  }

  system.eventStream.subscribe(system.actorOf(Props[Unhandler]), classOf[UnhandledMessage])

  val master = system.actorOf(Props[Master])
}