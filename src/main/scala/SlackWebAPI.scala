import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.{FormData, HttpRequest}
import spray.httpx.unmarshalling._
import spray.json.{JsObject, DefaultJsonProtocol}

import scala.concurrent.{ExecutionContext, Future}

/*
 * https://api.slack.com/web#basics
 */
object SlackWebAPI {
  case class Request(m:Map[String,String])

  case class Ok(ok:Boolean, error:String)
  object OkProtocol extends DefaultJsonProtocol {
    implicit val slackFormat = jsonFormat2(Ok)
  }
  import OkProtocol._

  /*
   * http://spray.io/documentation/1.2.3/spray-client/#usage
   */

  def createPipeline[T](method:String, parser:JsObject => T)(implicit context:ExecutionContext, system:ActorSystem)
    : Request => Future[Either[String, T]] = {

    val pipeline:HttpRequest => Future[JsObject] = sendReceive ~> unmarshal[JsObject]
    val url = s"https://slack.com/api/$method"

    {
      case Request(args) =>
        val request:HttpRequest = Post(url, FormData(args))
        val response:Future[JsObject] = pipeline(request)
        response.map(json => {
          json.convertTo[Ok] match {
            case Ok(ok, msg) if ok => Right(parser(json))
            case Ok(ok, msg) if !ok => Left(msg)
          }
        })
    }
  }
}