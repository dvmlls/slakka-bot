package cat.dvmlls.slack.web

import spray.json._

/*
 * https://gist.github.com/mikemckibben/fad4328de85a79a06bf3
 */

object Protocol {
  case class Success[A](result: A)

  object Success {

    implicit def successFormat[A](implicit format: JsonFormat[A]) = new RootJsonFormat[Success[A]] {

      override def write(value: Success[A]): JsValue = {
        JsObject("ok" -> JsBoolean(true), "result" -> format.write(value.result))
      }

      override def read(json: JsValue): Success[A] = {
        val root = json.asJsObject
        (root.fields.get("ok"), root.fields.get("result")) match {
          case (Some(JsTrue), Some(jsValue)) => Success(format.read(jsValue))

          case _ => deserializationError("JSON not a Success")
        }
      }
    }

  }

  case class Failure(reason: String)

  object Failure {

    implicit object failureFormat extends RootJsonFormat[Failure] {

      override def write(value: Failure): JsValue = {
        JsObject("ok" -> JsBoolean(false), "error" -> JsString(value.reason))
      }

      override def read(json: JsValue): Failure = {
        val root = json.asJsObject
        (root.fields.get("ok"), root.fields.get("error")) match {
          case (Some(JsFalse), Some(JsString(reason))) => Failure(reason)

          case _ => deserializationError("JSON not a Failure")
        }
      }
    }
  }

  type Result[A] = Either[Failure, Success[A]]

  implicit def rootEitherFormat[A : RootJsonFormat, B : RootJsonFormat] = new RootJsonFormat[Either[A, B]] {
    val format = DefaultJsonProtocol.eitherFormat[A, B]

    def write(either: Either[A, B]) = format.write(either)

    def read(value: JsValue) = format.read(value)
  }
}

object Protocol2 {
  implicit def slackFormat[T](implicit f:JsonFormat[T]) = new JsonFormat[Either[String, T]] {

    def write(either: Either[String, T]):JsValue = either match {
      case Left(s) => JsObject("ok" -> JsBoolean(false), "error" -> JsString(s))
      case Right(t) =>  JsObject("ok" -> JsBoolean(false), "error" -> JsString(s)) f.write(t)
    }

    def read(value: JsValue):Either[String,T] = format.read(value)
  }
}