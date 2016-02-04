import java.io.File

import akka.actor.{ActorLogging, Actor}
import scala.concurrent.Future
import scala.io.Source
//import scala.sys.process.processInternal.OutputStream
import scala.sys.process.{ProcessIO, ProcessLogger, Process}
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

//object ProcessActor2 {
//  implicit def string2seq(s:String):Seq[String] = s.split(' ')
//  case class Request(workingDirectory:File, args:Seq[String])
//  case class StdErr(s:String)
//  case class StdOut(s:String)
//  case class Finished(returnCode:Int)
//}
//
//class ProcessActor2 extends Actor with ActorLogging {
//  import ProcessActor2._
//  import akka.pattern.pipe
//  implicit val c = context.dispatcher
//
//  def working(stdin:OutputStream):Receive = { log.debug("state -> working"); {
//    case _ => ???
//  }}
//
//  def idle:Receive = { log.debug("state -> idle"); {
//    case Request(workingDirectory, args) => log.debug(s"cwd=$workingDirectory args=$args")
//      val b = Process(args, workingDirectory)
//
////      val l = new ProcessIO(
////        stdin => stdin.context.become(working(stdin)),
////        stdout => Source.fromInputStream(stdout).getLines().foreach(sender ! StdOut(_)),
////        stderr => Source.fromInputStream(stderr).getLines().foreach(sender ! StdErr(_))
////      )
////
////      Future { b.run(l).exitValue() }.mapTo[Int].map(Finished).pipeTo(sender())
//  }}
//
//  def receive = idle
//}