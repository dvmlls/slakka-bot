package slack

import spray.json.{CollectionFormats, DefaultJsonProtocol}

object SlackWebProtocol extends DefaultJsonProtocol with CollectionFormats {
  /* https://api.slack.com/web#basics */
  case class Ok(ok:Boolean, error:Option[String])
  implicit val okFormat = jsonFormat2(Ok)

  /* https://api.slack.com/methods/rtm.start */
  case class RTMSelf(id:String, name:String)
  case class RTMStart(url:String, self:RTMSelf)
  implicit val rtmSelfFormat = jsonFormat2(RTMSelf)
  implicit val rtmStartFormat = jsonFormat2(RTMStart)

  /* https://api.slack.com/methods/channels.list */
  case class Channel(id:String,name:String)
  case class ChannelList(channels:List[Channel])
  implicit val channelFormat = jsonFormat2(Channel)
  implicit val channelListFormat = jsonFormat1(ChannelList)

  /* https://api.slack.com/methods/users.list */
  case class Profile(email:Option[String])
  case class User(id:String, name:String, profile:Profile)
  case class UserList(members:List[User])
  implicit val profileFormat = jsonFormat1(Profile)
  implicit val userFormat = jsonFormat3(User)
  implicit val userListFormat = jsonFormat1(UserList)

  /* https://api.slack.com/methods/im.open */
  case class IMChannel(id:String)
  case class IMOpen(channel:IMChannel)
  implicit val imChannelFormat = jsonFormat1(IMChannel)
  implicit val imOpenFormat = jsonFormat1(IMOpen)

  /* https://api.slack.com/methods/im.list */
  case class IM(id:String, user:String)
  case class IMList(ims:List[IM])
  implicit val imFormat = jsonFormat2(IM)
  implicit val imListFormat = jsonFormat1(IMList)

  /* https://api.slack.com/methods/channels.join */
  case class ChannelJoin(channel:Channel)
  implicit val channelJoinFormat = jsonFormat1(ChannelJoin)

  /* https://api.slack.com/methods/reactions.add */
  case class ReactionAdd(channel:String, timestamp:String, name:String)
  implicit val reactionAddFormat = jsonFormat3(ReactionAdd)
}