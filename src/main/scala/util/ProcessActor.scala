package util

import java.io.{File, OutputStream}
import akka.actor.{PoisonPill, ActorLogging, Actor}
import scala.concurrent.Future
import scala.io.Source
import scala.sys.process.{Process, ProcessIO}
import scala.collection.mutable

object ProcessActor {
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

class ProcessActor extends Actor with ActorLogging {
  import ProcessActor._
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