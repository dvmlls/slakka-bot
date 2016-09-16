package util

import java.time.Instant

import spray.json._

trait MoreJsonProtocols {
  /*
   * http://stackoverflow.com/a/25417819/908042
   */
  type RJF[T] = RootJsonFormat[T]
  implicit def rootEitherFormat[A:RJF,B:RJF]: RJF[Either[A, B]] = new RJF[Either[A, B]] {
    val format = DefaultJsonProtocol.eitherFormat[A, B]
    def write(either: Either[A, B]) = format.write(either)
    def read(value: JsValue) = format.read(value)
  }

  /*
   * https://groups.google.com/forum/#!topic/spray-user/nJBCaCiFvGo
   */
  implicit object TimestampJsonFormat extends JsonFormat[Instant] {
    def write(x: Instant): JsString = JsString("farts")
    def read(value: JsValue): Instant = value match {
      case JsString(s) => Instant.parse(s)
      case x => deserializationError("Expected Timestamp as JsString, but got " + x)
    }
  }
}