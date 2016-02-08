import java.net.URI
import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import akka.pattern.pipe
import slack.{SlackWebProtocol, SlackWebAPI, SlackRTProtocol}
import SlackRTProtocol._
import SlackWebProtocol._
import util.WebSocketClient

object Bot extends App {
  type Lookup[T] = String => Option[T]

  val token = sys.env("SLACK_TOKEN")

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  val baseMap = Map("token" -> token)
  val blankRequest = SlackWebAPI.Request(baseMap)

  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")
  val channels = SlackWebAPI.createPipeline[ChannelList]("channels.list")
  val imOpen = SlackWebAPI.createPipeline[IMOpen]("im.open")
  val imList = SlackWebAPI.createPipeline[IMList]("im.list")
  val users = SlackWebAPI.createPipeline[UserList]("users.list")
  val slackClient = system.actorOf(Props[WebSocketClient])

  case class Person(id:String, name:String, email:String)
  case class UserChat(username:String, message:String)

  class Brain extends Actor with ActorLogging {

    case class Startup (uri:URI, channels:List[Channel], users:List[Person], ims:List[IM])

    val f = for (
      RTMStart(url) <- rtmStart(blankRequest) ;
      ChannelList(channels) <- channels(blankRequest) ;
      UserList(users) <- users(blankRequest);
      IMList(ims) <- imList(blankRequest)
    ) yield {

      val us = for (
        user <- users;
        email <- user.profile.email
      ) yield Person(user.id, user.name, email)

      log.info(s"found websocket URL, users (${us.size}), and channels (${channels.size})")
      Startup(new URI(url), channels, us, ims)
    }

    f recover {
      case e:Exception =>
        log.error("couldn't start up", e)
        system.terminate()
    } pipeTo self

    def connected(channels:List[Channel], users:List[Person], ims:List[IM]):Receive = { log.info("connecting")

      {
        case WebSocketClient.Received(fromSlack) =>
          fromSlack.convertTo[Base] match {
            case Base(Some("message")) =>
              val m = fromSlack.convertTo[Message]

              for (
                im <- ims.find(_.id == m.channel);
                u <- users.find(_.id == im.user)
              ) log.info("" + UserChat(u.name, m.text))
            case _ => log.info(s"unhandled message: $fromSlack")
          }

        case UserChat(username, message) =>
          users.find(_.name == username).map(_.id) match {
            case Some(userId) =>
              for (
                IMOpen(IMChannel(channel)) <- imOpen(SlackWebAPI.Request(baseMap ++ Map("user" -> userId)))
              ) slackClient ! SlackRTProtocol.Message("message", channel, message).toJson
            case None => log.warning(s"couldn't find userId for that user: $username")
          }
      }
    }

    def receive:Receive = { log.info("disconnected"); {
      case Startup(uri, cs, us, ims) =>
        slackClient ! (uri, self)
        context.become(connected(cs, us, ims))
    }}
  }

  val brain = system.actorOf(Props[Brain])
}
