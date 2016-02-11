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

  def process(queue:mutable.Queue[SendMessage], target:ActorRef): Unit = {
    queue
      .groupBy(_.channel)
      .foreach {
        case (channel, messages) => target ! SendMessage(channel, messages.map(_.message).toList.mkString("\n"))
      }
    context.become(idle(target))
  }

  def throttling(target:ActorRef, timer:Cancellable, initial:SendMessage):Receive = {
    val queue = mutable.Queue[SendMessage](initial)
    var currentTimer = timer
    log.info("state -> throttling"); {
    case m:SendMessage =>
      queue.enqueue(m)
      timer.cancel()
      if (queue.size < 25) {
        val newTimer = context.system.scheduler.scheduleOnce(Duration.create(1, TimeUnit.SECONDS), self, Send())
        currentTimer = newTimer
      } else {
        process(queue, target)
      }
    case Send() =>
      process(queue, target)
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
