import scala.util.{Failure, Try, Success}
def print2(f: => Any): Unit = {
  Try {
    f
  } match {
    case Success(a) => println("> " + a)
    case Failure(x) => println("! " + x)
  }
}

// options
val empty:Option[String] = None
val full:Option[String] = Some("hello!")
empty.foreach(s => print2(s))
full.foreach(s => print2(s))

empty.map(_.toUpperCase).foreach(s => print2(s))
full.map(_.toUpperCase).foreach(s => print2(s))
empty.filter(_.contains("cats")).map(_.toUpperCase).foreach(s => print2(s))
full.filter(_.contains("cats")).map(_.toUpperCase).foreach(s => print2(s))
// case classes
case class Cat(name:String, superFluffy:Boolean)
val mc = Cat("Mister Cuddles", superFluffy=false)
val se = Cat("Senor Esnuggle", superFluffy=true)

print2 { se match { case Cat(name, superFluffy) if superFluffy => name  } }
print2 { mc match { case Cat(name, superFluffy) if superFluffy => name  } }
// futures
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
val fails:Future[String] = Future.failed(new Exception("goodbye."))
val succeeds = Future { "hello!" }
print(succeeds)
print(fails)
print(succeeds.map(_.toUpperCase))
print(fails.map(_.toUpperCase))
fails.filter(_.contains("cats")).map(_.toUpperCase).foreach(println)
succeeds.filter(_.contains("cats")).map(_.toUpperCase).foreach(println)
Await.ready(fails, Duration.Inf)
Await.ready(succeeds, Duration.Inf)
Thread.sleep(1000)

// regexes

val punctuation = " ,.:!?"
val myUserId = "U0K3W1BK3"
val myUserName = "dvbt"
val IdMention = s""".*[<][@]$myUserId[>][$punctuation]+(.+)""".r
//val NameMention = s""".*[@]$myUserName[$punctuation]+(.+)""".r
val NameMention = s"""[@]dvbt (.+)""".r
"<@U0K3W1BK3>: do some stuff" match {
  case NameMention(message) => s"name match: $message"
  case IdMention(message) => s"id match: $message"
  case _ => "doesn't match"
}

"@dbvt has some sass" match {
  case NameMention(message) => s"name match: $message"
  case IdMention(message) => s"id match: $message"
  case _ => "doesn't match"
}

val GithubFlowPattern = """.*GithubFlow ([^ ]+) ([^ ]+) ([^ ]+) ([\d]+) ([^ ]+)""".r
val m = "<@U0K3W1BK3>: GithubFlow WeConnect spaceman spaceman-production 1234 BILL-123"
m match {
  case NameMention(message) => s"name match: $message"
  case IdMention(GithubFlowPattern(a, b, c, d, e)) => s"id match: $a $b $c $d $e"
  case _ => "doesn't match"
}

"GithubFlow WeConnect spaceman spaceman-production 1234 BILL-123" match {
  case GithubFlowPattern(a,b,c,d,e) => (a,b,c,d,e)
  case _ => "no match"
}
