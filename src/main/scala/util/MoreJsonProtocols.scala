package util

import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat}

trait MoreJsonProtocols {
  /*
   * http://stackoverflow.com/a/25417819/908042
   */
  type RJF[T] = RootJsonFormat[T]
  implicit def rootEitherFormat[A:RJF,B:RJF] = new RJF[Either[A, B]] {
    val format = DefaultJsonProtocol.eitherFormat[A, B]
    def write(either: Either[A, B]) = format.write(either)
    def read(value: JsValue) = format.read(value)
  }
}