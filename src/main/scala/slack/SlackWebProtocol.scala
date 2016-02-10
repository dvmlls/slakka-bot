package slack

import spray.json.{CollectionFormats, DefaultJsonProtocol}

object SlackWebProtocol extends DefaultJsonProtocol with CollectionFormats {
  case class Ok(ok:Boolean, error:Option[String])
  implicit val okFormat = jsonFormat2(Ok)

  case class RTMStart(url:String)
  implicit val rtmStartFormat = jsonFormat1(RTMStart)

  case class Channel(id:String,name:String)
  case class ChannelList(channels:List[Channel])
  implicit val channelFormat = jsonFormat2(Channel)
  implicit val channelListFormat = jsonFormat1(ChannelList)

  case class Profile(email:Option[String])
  case class User(id:String, name:String, profile:Profile)
  case class UserList(members:List[User])
  implicit val profileFormat = jsonFormat1(Profile)
  implicit val userFormat = jsonFormat3(User)
  implicit val userListFormat = jsonFormat1(UserList)

  case class IMChannel(id:String)
  case class IMOpen(channel:IMChannel)
  implicit val imChannelFormat = jsonFormat1(IMChannel)
  implicit val imOpenFormat = jsonFormat1(IMOpen)

  case class IM(id:String, user:String)
  case class IMList(ims:List[IM])
  implicit val imFormat = jsonFormat2(IM)
  implicit val imListFormat = jsonFormat1(IMList)
}