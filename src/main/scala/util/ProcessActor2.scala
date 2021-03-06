package util

import java.io.{File, OutputStream}
import akka.actor.{PoisonPill, ActorLogging, Actor}
import scala.concurrent.Future
import scala.io.Source
import scala.sys.process.{Process, ProcessIO}
import scala.collection.mutable

object ProcessActor2 {
  import scala.language.implicitConversions
  implicit def string2seq(s:String):Seq[String] = s.split(' ')
  case class Run(args:Seq[String], workingDirectory:Option[File]=None)
  case class StdErr(s:String)
  case class StdOut(s:String)
  sealed trait Message
  case class WriteLine(s:String) extends Message
  case class Close() extends Message
  case class Finished(returnCode:Int)
}

class ProcessActor2 extends Actor with ActorLogging {
  import ProcessActor2._
  import akka.pattern.pipe
  implicit val c = context.dispatcher

  def working(stdin:OutputStream, queue:mutable.Queue[Message]):Receive = {
    log.debug("state -> working")

    def process(m:Message) = m match {
      case WriteLine(s) => stdin.write(s"$s\n".getBytes); stdin.flush()
      case Close() => stdin.close()
    }

    if (queue.nonEmpty) {
      log.info(s"processing ${ queue.length } messages in queue")
      queue.foreach(process)
    }

    {
      case m:Message => process(m)
    }
  }

  def idle:Receive = {

    log.debug("state -> idle")
    val queue = mutable.Queue[Message]()

    {
      case Run(args, workingDirectory) => log.debug(s"cwd=$workingDirectory args=$args")
        val b = Process(args, workingDirectory)

        val requester = sender()

        val l = new ProcessIO(
          stdin => context.become(working(stdin, queue)),
          stdout => Source.fromInputStream(stdout).getLines().foreach(requester ! StdOut(_)),
          stderr => Source.fromInputStream(stderr).getLines().foreach(requester ! StdErr(_))
        )

        Future { b.run(l).exitValue() }
          .map(Finished)
          .pipeTo(sender())
          .andThen { case _ => self ! PoisonPill }
      case m:Message => queue.enqueue(m)
    }
  }

  def receive:Receive = idle
}

object Process2Tester extends App {
  import java.util.concurrent.TimeUnit

  import akka.actor._
  import akka.util.Timeout

  import ProcessActor2._

  implicit val system = ActorSystem()
  val TIMEOUT = 10
  implicit val timeout = new Timeout(TIMEOUT, TimeUnit.SECONDS)

  class Watcher extends Actor with ActorLogging {
    def watching(p:ActorRef):Receive = {
      case Terminated(johnConnor) =>
        system.terminate()
        sys.exit()
      case a:Any => p ! a
    }
    def receive:Receive = {
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