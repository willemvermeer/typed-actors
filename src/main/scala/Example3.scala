import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Success

object Example3 extends App {

  def counter(count: Int): Behavior[Greet] =
    Behaviors.receiveMessage {
      message =>
        println(s"Received msg nr $count ${message.msg}")
        counter(count + 1)
    }

  case class Greet(msg: String, replyTo: ActorRef[Response])

  case class Response(txt: String)

  val rootBehavior: Behavior[Greet] =
    Behaviors.setup { context =>

    val greetCounter: ActorRef[Greet] = context.spawn(
      counter(0), "GreetCounter")

    Behaviors.receiveMessage {
    message =>
      println(s"Received a greeting ${message.msg}")
      greetCounter ! message
      message.replyTo !
        Response(s"You say ${message.msg} I say Goodbye!")
      Behaviors.same
    }
  }

  val system: ActorSystem[Greet] =
    ActorSystem(rootBehavior, "Example3")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor =
    system.executionContext

  List("Julia", "Emma", "Sophie").foreach {
    name =>
      val future: Future[Response] =
        system ? (ref => Greet(s"Hello $name", ref))

      future.onComplete {
        case Success(msg) =>
          println(s"Received answer: ${msg.txt}")
      }
  }

}