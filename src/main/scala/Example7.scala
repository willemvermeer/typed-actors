import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }

object Example7 extends App {

  case class Greet(msg: String)

  def greeter: Behavior[Greet] =
    Behaviors.receiveMessage {
      message =>
        println(s"Hello ${message.msg}")
        Behaviors.same
    }

  val rootBehavior: Behavior[SpawnProtocol] =
    Behaviors.setup { context =>
      SpawnProtocol.behavior
    }

  val system: ActorSystem[SpawnProtocol] =
    ActorSystem(rootBehavior, "Example3")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor =
    system.executionContext

  private val future: Future[ActorRef[Greet]] =
    system ? SpawnProtocol
      .Spawn(behavior = greeter, name = "Greeter")

  for (fut <- future) fut ! Greet("Scala")

}