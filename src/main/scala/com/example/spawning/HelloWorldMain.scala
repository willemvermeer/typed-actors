package com.example.spawning

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import akka.util.Timeout
import scala.concurrent.duration._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Literal copy of SpawnProtocol example on https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html
  */
object HelloWorldMain {
  val main: Behavior[SpawnProtocol] =
    Behaviors.setup { ctx ⇒
      // Start initial tasks
      // ctx.spawn(...)

      SpawnProtocol.behavior
    }
}

object Boot {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[SpawnProtocol] =
      ActorSystem(HelloWorldMain.main, "hello")

    // needed in implicit scope for ask (?)
    import akka.actor.typed.scaladsl.AskPattern._
    implicit val ec: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val greeter: Future[ActorRef[HelloWorld.Greet]] =
      system ? SpawnProtocol.Spawn(behavior = HelloWorld.greeter, name = "greeter", props = Props.empty)

    val greetedBehavior = Behaviors.receive[HelloWorld.Greeted] { (ctx, msg) ⇒
      ctx.log.info("Greeting for {} from {}", msg.whom, msg.from)
      Behaviors.stopped
    }

    val greetedReplyTo: Future[ActorRef[HelloWorld.Greeted]] =
      system ? SpawnProtocol.Spawn(greetedBehavior, name = "", props = Props.empty)

    for (greeterRef ← greeter; replyToRef ← greetedReplyTo) {
      greeterRef ! HelloWorld.Greet("Akka", replyToRef)
    }
  }
}

object HelloWorld {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  val greeter: Behavior[Greet] = Behaviors.receive { (ctx, msg) ⇒
    ctx.log.info("Hello {}!", msg.whom)
    msg.replyTo ! Greeted(msg.whom, ctx.self)
    Behaviors.same
  }
}