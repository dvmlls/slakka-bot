package util

import javax.websocket.MessageHandler.{Partial, Whole}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.{Done, NotUsed}
import akka.stream._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import slack.SlackWebAPI
import slack.SlackWebProtocol.{RTMSelf, RTMStart}
import spray.json.{JsValue, JsonParser}

import scala.concurrent.Future
import scala.util.{Failure, Success}
import java.net.URI
import javax.websocket._

import org.glassfish.tyrus.client.ClientManager

object WebSocketClient {
  sealed trait Signal
  final case class Received(message:JsValue) extends Signal
  final case class ReceivedWhole(message:String) extends Signal
  case object Disconnected extends Signal
  final case class ReceivedPartial(message:String, last:Boolean) extends Signal
  case object Connected extends Signal
}

/*
 * http://stackoverflow.com/questions/26452903/javax-websocket-client-simple-example
 * https://tyrus.java.net/documentation/1.12/user-guide.html#d0e78
 */
class WebSocketClient extends Actor with ActorLogging {
  import WebSocketClient._

  def connect(server:URI, client:ActorRef) {
    val endpoint = new Endpoint {
      override def onOpen(session: Session, config: EndpointConfig) {
        session.addMessageHandler(new Whole[String] {
          override def onMessage(message: String) {
            val parsed = JsonParser(message)
            client ! Received(parsed)
          }
        })
/* i don't think Slack does this */
//        session.addMessageHandler(new Partial[String] {
//          override def onMessage(message: String, last:Boolean) {
//            log.debug(s"received message ${if (last) "fragment" else "ending"}: $message")
//            client ! ReceivedPartial(message, last)
//          }
//        })
        context.become(connected(session))
      }
      override def onClose(session:Session, reason:CloseReason) = {
        log.warning(s"endpoint closed: $reason")
        context.become(disconnected)
        client ! Disconnected
      }
      override def onError(session:Session, error:Throwable) = {
        log.error(s"error", error)
        throw error
      }
    }
    val config = ClientEndpointConfig.Builder.create().build()
    val c = ClientManager.createClient()
    c.connectToServer(endpoint, config, server)
  }

  def connected(session:Session):Receive = { log.info("state -> connected") ; {
    case message:JsValue =>
      log.debug(s"sending message: $message")
      session.getAsyncRemote.sendText(message.compactPrint)
  }}

  def disconnected:Receive = { log.info("state -> disconnected"); {
    case server:URI =>
      log.info(s"connecting to: $server")
      connect(server, context.parent)
  }}

  override def receive: Receive = disconnected
}

class WebSocketClient2 extends Actor with ActorLogging {
  import WebSocketClient._

  def connect(server:URI, client:ActorRef) {
    val endpoint = new Endpoint {
      override def onOpen(session: Session, config: EndpointConfig) {
        session.addMessageHandler(new Whole[String] {
          override def onMessage(message: String) { client ! ReceivedWhole(message) }
        })
        session.addMessageHandler(new Partial[String] {
          override def onMessage(message: String, last:Boolean) { client ! ReceivedPartial(message, last) }
        })
        context.become(connected(session))
        client ! Connected
      }
      override def onClose(session:Session, reason:CloseReason) = {
        log.warning(s"endpoint closed: $reason")
        context.become(disconnected)
        client ! Disconnected
      }
      override def onError(session:Session, error:Throwable) = {
        log.error(s"error", error)
        throw error
      }
    }
    val config = ClientEndpointConfig.Builder.create().build()
    val c = ClientManager.createClient()
    c.connectToServer(endpoint, config, server)
  }

  def connected(session:Session):Receive = { log.info("state -> connected") ; {
    case message:String =>
      log.debug(s"sending message: $message")
      session.getAsyncRemote.sendText(message)
  }}

  def disconnected:Receive = { log.info("state -> disconnected"); {
    case server:URI =>
      log.info(s"connecting to: $server")
      connect(server, sender())
  }}

  override def receive: Receive = disconnected
}

/*
http://blog.kunicki.org/blog/2016/07/20/implementing-a-custom-akka-streams-graph-stage/
https://github.com/akka/akka-stream-contrib/blob/master/amqp/src/main/scala/akka/stream/contrib/amqp/AmqpSource.scala#L66
*/
final class WebsocketFlow (server:URI) extends GraphStage[FlowShape[String, String]] {

  val inlet = Inlet[String]("inlet")
  val outlet = Outlet[String]("outlet")

  override def shape: FlowShape[String, String] = FlowShape(inlet, outlet)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    def pushMessage(m:String): Unit = { push(outlet, m) }
    val push_ = getAsyncCallback(pushMessage)
    val fail_ = getAsyncCallback(failStage)
    def complete(r:CloseReason) { completeStage() }
    val complete_ = getAsyncCallback(complete)

    val endpoint = new Endpoint {
      override def onOpen(session: Session, config: EndpointConfig) {
        println("onOpen")
        session.addMessageHandler(new Whole[String] {
          override def onMessage(message: String): Unit = { println("onMessage"); push_.invoke(message) }
        })
      }
      override def onClose(session:Session, reason:CloseReason) = { println("onComplete"); complete_.invoke(reason) }
      override def onError(session:Session, error:Throwable) = { println("onErr"); fail_.invoke(error) }
    }
    val config = ClientEndpointConfig.Builder.create().build()
    val session = ClientManager.createClient().connectToServer(endpoint, config, server)
    val remote = session.getAsyncRemote

    setHandler(inlet, new InHandler {
      override def onPush(): Unit = { remote.sendText(grab(inlet)) }
    })

    setHandler(outlet, new OutHandler {
      override def onPull(): Unit = { }
    })
  }
}

object Test1 extends App { import akka.stream.scaladsl._
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  val source: Source[String, SourceQueueWithComplete[String]] = Source.queue[String](bufferSize=10, OverflowStrategy.fail)
  val queue: SourceQueueWithComplete[String] = source.to(Sink.foreach(println)).run()
  queue.offer("farts")
}

object Test2 extends App { import akka.stream.scaladsl._
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val slackToken = SlackWebAPI.Token( system.settings.config.getString("amy.slack.token") )

  lazy val wsc = system.actorOf(Props { new WebSocketClient2() }, "wsc")

  def things(url:String): Unit = {
    val source = Source.queue[String](bufferSize=10, OverflowStrategy.fail)
    val printer: Sink[String, Future[Done]] = Sink.foreach[String](println)
    val graph = new WebsocketFlow(new URI(url))
    val flow = Flow.fromGraph(graph)

    val sink: Sink[String, NotUsed] = flow.to(printer)

    val queue = source.to(sink).run()

//    queue.offer("farts")
  }

  val rtmStart = SlackWebAPI.createPipeline[RTMStart]("rtm.start")

  rtmStart(Map()).onComplete {
    case Success(RTMStart(url, RTMSelf(id, name))) => things(url)
    case Failure(ex) =>
  }
}