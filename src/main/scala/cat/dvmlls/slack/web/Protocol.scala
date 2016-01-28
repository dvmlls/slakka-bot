package cat.dvmlls.slack.web

import spray.json.DefaultJsonProtocol

case class Ok(ok:Boolean, error:Option[String])
case class RTMStart(url:String)
object Protocol extends DefaultJsonProtocol {
  /*
   * slack always returns a json object with a boolean 'ok' field
   *  - if false: they'll popoulate the 'error' field
   *  - if true: the object will have the data i want
   */
  implicit val okFormat = jsonFormat2(Ok)

  implicit val rtmStartFormat = jsonFormat1(RTMStart)
}