import java.util.concurrent.TimeUnit
import akka.actor._
import akka.util.Timeout
import akka.pattern.{ask,pipe}
import bots.TickerClient

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
import system.dispatcher

val kernel = system.actorOf(Props { new TickerClient() }, "kernel")

// kernel ! "BWLD"
// kernel ! "ASDFJ"