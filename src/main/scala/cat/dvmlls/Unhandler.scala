package cat.dvmlls

import akka.actor.{Actor, ActorLogging, UnhandledMessage}

class Unhandler extends Actor with ActorLogging {
  def receive:Receive = {
    case UnhandledMessage(msg, sender, recipient) => log.info(s"not handled: $msg")
  }
}