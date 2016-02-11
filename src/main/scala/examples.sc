var section = ""
section = "--------------------------"
section = "|      CASE CLASSES      |"
section = "--------------------------"
trait SockLength
case object Crew extends SockLength
case object Ankle extends SockLength
case object Knee extends SockLength
case class Sock (length:SockLength, isArgyle:Boolean)
val mine = Sock(Ankle, isArgyle=true)
mine match {
  case Sock(_, true) => "you silly"
  case _ => "ok then"
}
val yours = Sock(Crew, isArgyle = false)
val mine2 = Sock(Ankle, isArgyle = true)
mine == yours
mine == mine2
mine eq mine2 // reference equality
section = "--------------------------"
section = "|   PARTIAL FUNCTIONS    |"
section = "--------------------------"
import scala.util.Try
Try { 4 / 0 }
def divide(numerator:Double, dominator:Double) = dominator match {
  case d:Double if d != 0 => numerator / dominator
}
Try { divide(4,0) }
val div:PartialFunction[(Double,Double),Double] = {
  case (numerator:Double, dominator:Double) if dominator != 0 =>
    numerator / dominator
}
Try { div(4,0) }
val safeDiv:PartialFunction[(Double,Double),Double] =
  div orElse { case _ => Double.NegativeInfinity }
safeDiv(4,0)
section = "--------------------------"
section = "|   FOR COMPREHENSIONS   |"
section = "--------------------------"
for (i <- 0 until 10) {
  print(i)
}
for (i <- 1 to 5) yield i
for (x <- List('a', 'b', 'c'); y <- 1 to 3) yield s"$x$y"
for (
  x <- List('m', 'n', 'o');
  y <- 1 to 5
  if y % 2 == 0
) yield s"[$x,$y]"
List('m', 'n', 'o')
  .flatMap(x => (1 to 5).filter(_ % 2 == 0).map(y => s"[$x,$y]"))
section = "--------------------------"
section = "|   PATTERN MATCHING     |"
section = "--------------------------"
val punctuation = " ,.:!?"
val myUserId = "U0K3W1BK3"
val IdMention = s""".*[<][@]$myUserId[>][$punctuation]+(.+)""".r
"<@U0K3W1BK3>: do some stuff" match {
  case IdMention(message) => s"id match: $message"
  case _ => "doesn't match"
}
"@dvbt: do some stuff" match {
  case IdMention(message) => s"id match: $message"
  case _ => "doesn't match"
}

val GithubFlowPattern = """.*GithubFlow ([^ ]+) ([^ ]+) ([^ ]+) ([\d]+) ([^ ]+)""".r
val m = "<@U0K3W1BK3>: GithubFlow WeConnect spaceman spaceman-production 1234 BILL-123"
m match {
  case IdMention(GithubFlowPattern(a, b, c, d, e)) =>
    s"id+flow match: $a $b $c $d $e"
  case _ => "doesn't match"
}

object MyExtractor {
  def unapply(s:String):Option[(Int,Boolean)] = s match {
    case "Dave rocks my socks" => Some((1, true))
    case _ => None
  }
}

"Dave rocks my socks" match {
  case MyExtractor(i, b) => s"he really is number $i, which is $b"
  case _ => "boo hoo"
}

"Karl and his big fat face" match {
  case MyExtractor(i, b) => s"he really is number $i, which is $b"
  case _ => "boo hoo"
}