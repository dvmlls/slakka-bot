import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import java.net.URI
import akka.pattern.pipe

object Listener extends App {
  val token = sys.env("SLACK_TOKEN")

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  import SlackWebProtocol._

  val blankRequest = SlackWebAPI.Request(Map("token" -> token))
  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
  val channels = SlackWebAPI.createPipeline[ChannelList]("channels.list")
  val users = SlackWebAPI.createPipeline[UserList]("users.list")
  val slackClient = system.actorOf(Props[WebSocketClient])

  class Master extends Actor with ActorLogging {

    case class Startup (uri:URI, channels:Map[String, String], users:Map[String, String])

    val f = for (
      RTMStart(url) <- rtmStart(blankRequest) ;
      ChannelList(channels) <- channels(blankRequest) ;
      UserList(users) <- users(blankRequest)
    ) yield {
      log.info(s"found websocket URL, users (${users.size}), and channels (${channels.size})")
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