package slack

import java.util.concurrent.TimeUnit

import akka.actor.{Cancellable, ActorRef, ActorLogging, Actor}
import slack.SlackChatActor.SendMessage

import scala.collection.mutable
import scala.concurrent.duration.Duration

object Throttle {
  case class Send()
}

class Throttle extends Actor with ActorLogging {
  import Throttle._

  implicit val ctx = context.system.dispatcher
  implicit val system = context.system

  def throttling(target:ActorRef, timer:Cancellable, initial:SendMessage):Receive = {
    val queue = mutable.Queue[SendMessage](initial)
    var currentTimer = timer
    log.info("state -> throttling"); {
    case m:SendMessage =>
      // store the message in a queue
      queue.enqueue(m)
      // reset the timer
      timer.cancel()
      val newTimer = context.system.scheduler.scheduleOnce(Duration.create(1, TimeUnit.SECONDS), self, Send())
      currentTimer = newTimer
    case Send() =>
      // send all my messages to the target
      queue.groupBy(_.channel).foreach {
        case (channel, messages) => target ! SendMessage(channel, messages.map(_.message).toList.mkString("\n")) }

      // go back to idle
      context.become(idle(target))
  }}

  def idle(target:ActorRef):Receive = { log.info("state -> idle"); {
    case m:SendMessage =>
      val timer = context.system.scheduler.scheduleOnce(Duration.create(1, TimeUnit.SECONDS), self, Send())
      context.become(throttling(target, timer, m))
  }}

  def receive = { log.info("state -> disconnected"); {
    case target:ActorRef => context.become(idle(target))
  }}
}
