package cat.dvmlls.slack.web

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.json.{JsObject, JsonReader}
import Protocol._
import spray.httpx.SprayJsonSupport._
import scala.concurrent.{ExecutionContext, Future}

/*
 * https://api.slack.com/web#basics
 * http://spray.io/documentation/1.2.3/spray-client/#usage
 */
object API {
  case class Request(m:Map[String,String])

  def createPipeline[T](method:String)
                       (implicit ctx:ExecutionContext, sys:ActorSystem, rdr:JsonReader[T]) : Request => Future[T] = {

    val pipeline:HttpRequest => Future[JsObject] = sendReceive ~> unmarshal[JsObject]
    val url = s"https://slack.com/api/$method"

    {
      case Request(args) =>
        val request:HttpRequest = Post(url, FormData(args))
        val response:Future[JsObject] = pipeline(request)

        response.flatMap(json => json.convertTo[Ok] match {
          case Ok(ok, Some(msg)) => Future.failed(new Exception(msg))
          case Ok(ok, None) => Future(json.convertTo[T])
        })
    }
  }
}