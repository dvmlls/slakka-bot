package bots

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}
import scala.concurrent.ExecutionContext

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}

import spray.json.DefaultJsonProtocol
import spray.client.pipelining._
import spray.http._
import spray.httpx.unmarshalling._
import spray.httpx.SprayJsonSupport._

import slack.ChannelService.ChannelId
import slack.SlackChatActor.MessageReceived
import slack.SlackWebAPI
import slack.SlackWebProtocol.{ChatPost, RTMSelf}
import util.MoreJsonProtocols

object GoogleFinanceProtocol extends DefaultJsonProtocol with MoreJsonProtocols {
  case class Quote(t:String, e:String, l:String)
  implicit val contentListingFormat = jsonFormat3(Quote)
}

trait GoogleFinanceAPI {
  import GoogleFinanceProtocol._

  implicit def as:ActorSystem
  implicit def ec:ExecutionContext = as.dispatcher

  def clean(response:HttpResponse):HttpResponse = {
    if (response.status.isSuccess) {
      /* they put two /s and a space at the front: drop them */
      val newBody = response.entity.asString.trim.drop(3)

      /* they have the content type as text/html which spray doesn't like: fix it */
      val newContentType = ContentType(MediaTypes.`application/json`)

      HttpResponse(response.status, HttpEntity(newContentType, newBody), response.headers, response.protocol)
    } else {
      response
    }
  }

  def query(ticker:String) = {
    val pipeline = sendReceive ~> clean ~> unmarshal[List[Quote]]
    pipeline(Get(s"http://finance.google.com/finance/info?&q=NSE:$ticker"))
  }
}

class TickerClient extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  object API extends GoogleFinanceAPI { override val as = context.system }

  def receive = {
    case s:String => API.query(s).onComplete {
      case Success(q) => println(q)
      case Failure(ex) => log.error(ex, "")
    }
  }
}

object StockBot {
  val TickerPattern = """.*[$]([A-Z]{1,5}([:][A-Z]+)?).*""".r
}

class Refresher(ticker:String, channelId:String, ts:String)(implicit val t:SlackWebAPI.Token) extends Actor with ActorLogging {
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val updatePost = SlackWebAPI.createPipeline[ChatPost]("chat.update")
  object GoogleAPI extends bots.GoogleFinanceAPI { override val as = context.system }

  def receive = { log.info("state -> refreshing")
    val delay = FiniteDuration(1, TimeUnit.SECONDS)
    val timer = context.system.scheduler.schedule(delay, delay, self, true)
    var remaining = 500

    {
      case c:Boolean =>
        val f = for (
          quotes <- GoogleAPI.query(ticker);
          ack <- updatePost(Map("channel" -> channelId, "text" -> s"$remaining: ${quotes.head.toString}", "ts" -> ts))
        ) yield ack

        f.onComplete {
          case Success(c:ChatPost) => remaining -= 1
          case Failure(ex) => log.error(ex, "failure refreshing quote: " + ticker)
        }

        if (remaining <= 0) {
          timer.cancel()
          self ! PoisonPill
        }
    }
  }
}

class StockBot(implicit val token:SlackWebAPI.Token) extends Actor with ActorLogging {
  import StockBot._
  implicit val ctx = context.dispatcher
  implicit val sys = context.system

  val chatPost = SlackWebAPI.createPipeline[ChatPost]("chat.postMessage")

  object GoogleAPI extends bots.GoogleFinanceAPI { override val as = context.system }

  def connected(myUserId:String, myUserName:String):Receive = { log.info("state -> connected");
    {
      case MessageReceived(ChannelId(channelId), _, TickerPattern(ticker, _), Some(ts)) =>
        val f = for (
          quotes <- GoogleAPI.query(ticker);
          ack <- chatPost(Map("channel" -> channelId, "text" -> quotes.head.toString))
        ) yield ack

        f.onComplete {
          case Success(ChatPost(c, t)) => context.actorOf(Props { new Refresher(ticker, c, t) })
          case Failure(ex) => log.error(ex, "failure getting initial quote: " + ticker)
        }
    }
  }

  def receive:Receive = { log.info("state -> disconnected"); {
    case RTMSelf(id, name) => context.become(connected(id, name))
  }}
}

