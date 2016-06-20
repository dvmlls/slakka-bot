package git

import java.util.concurrent.TimeUnit
import akka.actor.{Status, _}
import git.StatusActor._
import scala.concurrent.duration.Duration

class StatusPoller extends Actor with ActorLogging {
  implicit val ctx = context.dispatcher

  val s = context.actorOf(Props[StatusActor], "status")

  def pending(c:CheckCIStatus, requester:ActorRef):Receive = { log.debug("state -> pending"); {
    case CIPending() => schedule(c)
    case u:CIUnknown => schedule(c)
      context.become(unknown(c, requester) orElse handleTerminalStates(requester))
  }}

  def handleTerminalStates(requester:ActorRef):Receive = {
    def terminate(a:Any): Unit = { requester ! a ; context.become(idle) }

    {
      case s:CISuccess => terminate(s)
      case f:CIFailure => terminate(f)
      case e:CIError => terminate(e)
      case x:Status.Failure => terminate(x)
    }
  }

  def unknown(c:CheckCIStatus, requester:ActorRef, retriesLeft:Int=6):Receive = { log.debug("state -> unknown"); {
    case CIPending() =>
      context.become(pending(c, requester) orElse handleTerminalStates(requester))
      schedule(c)
    case u:CIUnknown =>
      if (retriesLeft <= 0) {
        context.become(idle)
        requester ! u
      } else {
        context.become(unknown(c, requester, retriesLeft-1) orElse handleTerminalStates(requester))
        schedule(c)
      }
  }}

  val interval = Duration.create(10, TimeUnit.SECONDS)
  def schedule(c:CheckCIStatus) { context.system.scheduler.scheduleOnce(interval, s, c) }

  def idle:Receive = { log.debug("state -> idle"); {
      case c:CheckCIStatus =>
        s ! c
        context.become(unknown(c, sender()) orElse handleTerminalStates(sender()))
  }}

  def receive = idle
}