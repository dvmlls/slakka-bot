import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import cat.dvmlls.WebSocketClient
import cat.dvmlls.slack.web._
import java.net.URI
import akka.pattern.pipe

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
  val slackClient = system.actorOf(Props[WebSocketClient])

  class Master extends Actor with ActorLogging {

    case class Startup (uri:URI, channels:Map[String, String], users:Map[String, String])

    val f = for (
      RTMStart(url) <- rtmStart(blankRequest) ;
      ChannelList(channels) <- channels(blankRequest) ;
      UserList(users) <- users(blankRequest)
    ) yield {
      log.info("found websocket URL, users, and channels")
      Startup(new URI(url),
        channels.map(c => (c.id, c.name)).toMap,
        users.flatMap(u => u.profile.email.map(email => (u.id, email))).toMap)
    }

    f recover {
      case e:Exception =>
        log.error("couldn't start up", e)
        system.terminate()
    } pipeTo self

    def connected(channels:Map[String, String], users:Map[String,String]):Receive = { log.info("connecting"); {
      case toSlack:String => slackClient ! toSlack
      case WebSocketClient.Received(fromSlack) =>
    }}

    def receive:Receive = { log.info("disconnected"); {
      case Startup(uri, cs, us) =>
        slackClient ! (uri, self)
        context.become(connected(cs, us))
    }}
  }

  val master = system.actorOf(Props[Master])
}