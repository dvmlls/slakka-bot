import akka.actor.{ActorRef, ActorLogging, Actor}
import spray.json._

class JSONWrapper extends Actor with ActorLogging {

  def connected(server:ActorRef, client:ActorRef):Receive = {
    case fromServer:String => client ! fromServer.parseJson
    case fromClient:JsValue => server ! fromClient.compactPrint
  }

  override def receive:Receive = {
    case (server:ActorRef, client:ActorRef) => context.become(connected(server, client))
  }
}