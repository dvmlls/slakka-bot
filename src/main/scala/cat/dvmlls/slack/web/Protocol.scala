package cat.dvmlls.slack.web

import spray.json.{CollectionFormats, DefaultJsonProtocol}

case class Ok(ok:Boolean, error:Option[String])
case class RTMStart(url:String)
case class Channel(id:String,name:String)
case class ChannelList(channels:List[Channel])
case class Profile(email:Option[String])
case class User(id:String, name:String, profile:Profile)
case class UserList(members:List[User])

object Protocol extends DefaultJsonProtocol with CollectionFormats {
  /*
   * slack always returns a json object with a boolean 'ok' field
   *  - if false: they'll popoulate the 'error' field
   *  - if true: the object will have the data i want
   */
  implicit val okFormat = jsonFormat2(Ok)

  implicit val rtmStartFormat = jsonFormat1(RTMStart)

  implicit val channelFormat = jsonFormat2(Channel)
  implicit val channelListFormat = jsonFormat1(ChannelList)

  implicit val profileFormat = jsonFormat1(Profile)
  implicit val userFormat = jsonFormat3(User)
  implicit val userListFormat = jsonFormat1(UserList)
}