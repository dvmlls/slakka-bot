import javax.websocket.MessageHandler.Whole
//import javax.websocket.MessageHandler.Partial

import akka.actor.{ActorRef, ActorLogging, Actor}
import org.glassfish.tyrus.client.ClientManager
import java.net.URI
import javax.websocket._

/*
 * http://stackoverflow.com/questions/26452903/javax-websocket-client-simple-example
 * https://tyrus.java.net/documentation/1.10/user-guide.html#d0e78
 */
class WebSocketClient (target:ActorRef) extends Actor with ActorLogging {

  def disconnected:Receive = { log.info("disconnected") ; {
    case url:String =>
      log.info(s"connecting to: $url")
      val endpoint = new Endpoint {
        override def onOpen(session: Session, config: EndpointConfig) {
          session.addMessageHandler(new Whole[String] {
            override def onMessage(message: String) {
              log.debug(s"received message: $message")
              target ! message
            }
          })
//          session.addMessageHandler(new Partial[String] {
//            override def onMessage(message: String, last:Boolean) {
//              log.debug(s"received message ${if (last) "fragment" else "ending"}: $message")
//              target ! message
//            }
//          })
          context.become(connected(session))
        }
        override def onClose(session:Session, reason:CloseReason) = { }
        override def onError(session:Session, error:Throwable) = { }
      }
      val config = ClientEndpointConfig.Builder.create().build()
      ClientManager.createClient().connectToServer(endpoint, config, new URI(url))
  }}

  def connected(session:Session):Receive = { log.info("connected") ; {
    case message:String =>
      log.debug(s"sending message: $message")
      session.getAsyncRemote.sendText(message)
  }}

  override def receive: Receive = disconnected
}