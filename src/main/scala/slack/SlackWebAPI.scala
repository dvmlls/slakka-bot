package slack

import SlackWebProtocol._
import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.httpx.SprayJsonSupport._
import spray.json.{JsObject, JsonReader}
import scala.concurrent.{ExecutionContext, Future}

/*
 * https://api.slack.com/web#basics
 * http://spray.io/documentation/1.2.3/spray-client/#usage
 */
object SlackWebAPI {
  case class Token(s:String)
  def createPipeline[T](method:String)
                       (implicit ctx:ExecutionContext, system:ActorSystem, rdr:JsonReader[T], token:Token
                       ) : Map[String,String] => Future[T] = {

    val pipeline:HttpRequest => Future[JsObject] = sendReceive ~> unmarshal[JsObject]
    val url = s"https://slack.com/api/$method"

    {
      args:Map[String,String] =>
        val request:HttpRequest = Post(url, FormData(args ++ Map("token" -> token.s)))
        val response:Future[JsObject] = pipeline(request)

        /*
         * slack always returns a json object with a boolean 'ok' field
         *  - if false: they'll populate the 'error' field
         *  - if true: the object will have the data i want
         */

        response.flatMap(json => json.convertTo[Ok] match {
          case Ok(true, None) => Future(json.convertTo[T])
          case Ok(false, Some(msg)) => Future.failed(new Exception(msg))
        })
    }
  }
}