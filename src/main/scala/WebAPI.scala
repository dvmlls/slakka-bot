import akka.actor.{ActorLogging, ActorSystem, Actor}
import akka.pattern.pipe
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.httpx.unmarshalling._

import scala.concurrent.{ExecutionContext, Future}

object WebAPI {

  case class Request(m:Map[String,String])

  def createPipeline[T](method:String)
                       (implicit unmarshaller:FromResponseUnmarshaller[T],
                         context:ExecutionContext,
                         system:ActorSystem):Map[String,String] => Future[T] = {

    val pipeline: HttpRequest => Future[T] = sendReceive ~> unmarshal[T]
    val url = s"https://slack.com/api/$method"

    (parameters:Map[String,String]) => pipeline(Post(url, FormData(parameters)))
  }
}

class WebAPI[T](method:String)(implicit unmarshaller:FromResponseUnmarshaller[T],
                               context:ExecutionContext,
                               system:ActorSystem) extends Actor with ActorLogging {

  lazy val pipeline = WebAPI.createPipeline[T](method)

  def receive:Receive = { case r:WebAPI.Request => pipeline(r.m).pipeTo(sender()) }
}