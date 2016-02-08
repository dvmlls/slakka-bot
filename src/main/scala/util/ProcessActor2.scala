package util

import java.io.{File, OutputStream}
import akka.actor.{PoisonPill, ActorLogging, Actor}
import scala.concurrent.Future
import scala.io.Source
import scala.sys.process.{Process, ProcessIO}

object ProcessActor2 {
  import scala.language.implicitConversions
  implicit def string2seq(s:String):Seq[String] = s.split(' ')
  case class Run(args:Seq[String], workingDirectory:Option[File]=None)
  case class StdErr(s:String)
  case class StdOut(s:String)
  case class WriteLine(s:String)
  case class Close()
  case class Finished(returnCode:Int)
}

class ProcessActor2 extends Actor with ActorLogging {
  import ProcessActor2._
  import akka.pattern.pipe
  implicit val c = context.dispatcher

  def working(stdin:OutputStream):Receive = { log.debug("state -> working"); {
    case WriteLine(s) => stdin.write(s"$s\n".getBytes)
    case Close() => stdin.close()
  }}

  def idle:Receive = { log.debug("state -> idle"); {
    case Run(args, workingDirectory) => log.debug(s"cwd=$workingDirectory args=$args")
      val b = Process(args, workingDirectory)

      val requester = sender()

      val l = new ProcessIO(
        stdin => context.become(working(stdin)),
        stdout => Source.fromInputStream(stdout).getLines().foreach(requester ! StdOut(_)),
        stderr => Source.fromInputStream(stderr).getLines().foreach(requester ! StdErr(_))
      )

      Future { b.run(l).exitValue() }
        .map(Finished)
        .pipeTo(sender())
        .andThen { case _ => self ! PoisonPill }
  }}

  def receive = idle
}

object Process2Tester extends App {
  import java.io.File
  import java.util.concurrent.TimeUnit

  import akka.actor._
  import akka.util.Timeout
  import scala.concurrent.Future
  import scala.io.Source
  import java.io.OutputStream

  import scala.sys.process.{ProcessIO, ProcessLogger, Process}

  import ProcessActor2._

  implicit val system = ActorSystem()
  implicit val timeout = new Timeout(10, TimeUnit.SECONDS)
  import system.dispatcher

  class Watcher extends Actor with ActorLogging {
    def watching(p:ActorRef):Receive = {
      case Terminated(johnConnor) =>
        system.terminate()
        sys.exit()
      case a:Any => p ! a
    }
    def receive = {
      case r:Run =>
        val p = context.actorOf(Props[ProcessActor2])
        context.watch(p)
        p ! r
        context.become(watching(p))
    }
  }
  val w = system.actorOf(Props[Watcher])

  w ! Run("heroku run rails c -a spaceman-staging")
}