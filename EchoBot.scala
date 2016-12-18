import java.util.concurrent.TimeUnit

import akka.actor._
import akka.util.Timeout
import bots.StandaloneBot
import slack._

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)

implicit val token = SlackWebAPI.Token(sys.env("SLACK_TOKEN"))

val kernel = system.actorOf(Props { new StandaloneBot() }, "kernel")