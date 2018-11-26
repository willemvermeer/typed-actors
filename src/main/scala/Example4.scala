import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.Success

object Example4 extends App {

  def calculatorBehavior(count: Int): Behavior[Operation] = {
    def process(result: Int, replyTo: ActorRef[Int]) = {
      println(s"Result = $result")
      replyTo ! result
      calculatorBehavior(result)
    }

    Behaviors.receiveMessage {
      case cmd: Add =>
        process(count + cmd.increment, cmd.replyTo)
      case cmd: Subtract =>
        process(count - cmd.decrement, cmd.replyTo)
    }
  }

  sealed trait Operation
  case class Add(increment: Int, replyTo: ActorRef[Int])
    extends Operation
  case class Subtract(decrement: Int, replyTo: ActorRef[Int])
    extends Operation

  val system: ActorSystem[Operation] =
    ActorSystem(calculatorBehavior(0), "Example4")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext

  val future: Future[Int] = system ? (ref => Add(5, ref))
  future.flatMap {
    _ => system ? ((ref: ActorRef[Int]) => Subtract(7, ref))
  }.onComplete {
    case Success(x) => println(s"End result = $x")
  }

}