import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask, pipe}
import bots.ShellBot
import slack.ChannelService.ChannelId
import slack.SlackChatActor.{MessageReceived, SendMessage}
import slack.UserService.{All, UserId, UserName}
import slack._
import slack.SlackWebProtocol._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

val authorized = List("dave")

val kernel = system.actorOf(Props{ new ShellBot(authorized) }, "kernel")