import java.util.concurrent.TimeUnit
import akka.util.Timeout
import akka.actor.ActorSystem
import jira.JiraWebAPI
import jira.JiraWebAPI.UUEncUP
import jira.JiraWebProtocol._
import spray.client.pipelining._
import spray.httpx.SprayJsonSupport._

import scala.util.{Success, Failure}

implicit val system = ActorSystem()
implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
implicit val d = system.dispatcher
implicit val u = UUEncUP(sys.env("JIRA_PASSWORD"))

val p = JiraWebAPI.pipeline[Issue]

p(Get("https://wework.atlassian.net/rest/api/2/issue/BILL-711")).onComplete {
  case Success(issue) => println(issue)
  case Failure(ex) => println(ex)
}
