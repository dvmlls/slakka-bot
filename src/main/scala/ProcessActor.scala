import java.io.File

import akka.actor.{ActorLogging, Actor}
import scala.sys.process.ProcessLogger
import scala.sys.process.Process
import scala.language.implicitConversions

object ProcessActor {
  implicit def string2seq(s:String):Seq[String] = s.split(' ')
  case class Request(workingDirectory:File, args:Seq[String])
  case class StdErr(s:String)
  case class StdOut(s:String)
  case class Finished(returnCode:Int)
}

class ProcessActor extends Actor with ActorLogging {
  import ProcessActor._
  implicit val c = context.dispatcher

  def receive = {
    case Request(workingDirectory, args) =>
      val l = ProcessLogger(sender ! StdOut(_), sender ! StdErr(_))
      val p = Process(args, workingDirectory)

      log.debug("" + p)

      sender() ! Finished(p ! l)
  }
}