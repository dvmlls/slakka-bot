package oauth

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse, Uri}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object GetAccessCode extends App {

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  val clientId = "4439240800.4472142127"
  val state = UUID.randomUUID().getMostSignificantBits
  val authUrl = s"https://slack.com/oauth/authorize?client_id=$clientId&state=$state"

  val response = pipeline(Get(Uri(authUrl))).map(_.headers.filter(_.name == "Location").head.value)

  println("go here in your browser: " + Await.result(response, Duration.Inf))
  system.shutdown()
}
