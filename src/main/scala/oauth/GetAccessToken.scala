package oauth

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.util.Timeout
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse, Uri}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object GetAccessToken extends App {
  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import oauth.GetAccessToken.system.dispatcher
  val clientId = "4439240800.4472142127"

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  val clientSecret = "7f57a51f45371acf836fb64ec772d1a7"
  val code = "4439240800.4462179252.53662dedac"
  val accessUrl = s"https://slack.com/api/oauth.access?client_id=$clientId&client_secret=$clientSecret&code=$code"
  val result = pipeline(Get(Uri(accessUrl)))

  println("status: " + Await.result(result, Duration.Inf))
  system.shutdown()
}
