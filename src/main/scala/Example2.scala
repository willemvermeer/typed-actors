import akka.actor.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Success

object Example2 extends App {

  case class Greet(msg: String, replyTo: ActorRef[Response])

  case class Response(txt: String)

  val rootBehavior: Behavior[Greet] =
    Behaviors.receiveMessage {
      message =>
        println(s"Received a greeting ${message.msg}")
        message.replyTo !
          Response(s"You say ${message.msg} I say Goodbye!")
        Behaviors.same
    }

  val system: ActorSystem[Greet] =
    ActorSystem(rootBehavior, "Example2")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor =
    system.executionContext

  val future: Future[Response] =
    system ? (ref => Greet("Hello", ref))

  future.onComplete {
    case Success(msg) => println(s"Received answer: ${msg.txt}")
  }

}
