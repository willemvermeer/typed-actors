import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }

object Example1 extends App {

  case class Greet(msg: String)

  val rootBehavior: Behavior[Greet] =
    Behaviors.receiveMessage {
      message =>
        println(s"Received a greeting ${message.msg}")
        Behaviors.same
    }

  val actorSystem: ActorSystem[Greet] =
    ActorSystem(rootBehavior, "Example1")

  actorSystem ! Greet("Hello")

}
