package com.example

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object MultiResponseAsk extends App {

  sealed trait Prints
  case class PrintMe(message: String, i: Int, ref: ActorRef[Response]) extends Prints

  sealed trait Response
  case class Response1(response: String) extends Response
  case class Response2(response: String) extends Response

  val printerBehavior: Behavior[Prints] = Behaviors.receive {
    case (ctx, PrintMe(msg, i, ref)) =>
      println(s"Received message $msg with $i")
      if (i==1) ref ! Response1(s"Thank you for sending us $msg")
      else ref ! Response2(s"Response2 to $msg")
      Behaviors.same
  }

  val system = ActorSystem(printerBehavior, "simple-ask")

  val printer = system
  implicit val timeout: Timeout = 3.seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  private val future1: Future[Response] = printer ? (ref => PrintMe("test1", 1, ref))
  private val future2: Future[Response] = printer ? (ref => PrintMe("test2", 2, ref))

  future1.onComplete {
    case Success(r) => r match {
      case r1: Response1 => println(s"Executed fine $r with response $r1")
      case r2: Response2 => println(s"Executed fine $r with response $r2")
    }
    case Failure(t) => t.printStackTrace()
  }
  future2.onComplete {
    case Success(r) => r match {
      case r1: Response1 => println(s"Executed fine $r with response $r1")
      case r2: Response2 => println(s"Executed fine $r with response $r2")
    }
    case Failure(t) => t.printStackTrace()
  }

}

