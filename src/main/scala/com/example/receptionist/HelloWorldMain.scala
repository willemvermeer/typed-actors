package com.example.receptionist

import akka.actor.Scheduler
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.receptionist.{ Receptionist, ServiceKey }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior, Terminated }
import akka.util.Timeout
import com.example.receptionist.HelloWorld.Greet

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object HelloWorld {
  val HelloWorldServiceKey = ServiceKey[Greet]("helloWorld")

  final case class Greet(whom: String)

  val greeter: Behavior[Greet] =
    Behaviors.setup { ctx =>
      ctx.system.receptionist ! Receptionist.Register(HelloWorldServiceKey, ctx.self)
      var x = 0
      Behaviors.receive { (_, msg) ⇒
        ctx.log.info("Hello {}!", msg.whom)
        x = x + 1
        if (x==2) Behaviors.stopped else Behaviors.same
      }
    }
}

object HelloWorldMain {
  val main: Behavior[Listing] =
    Behaviors.setup[Listing] { ctx ⇒
      ctx.system.receptionist ! Receptionist.Subscribe(HelloWorld.HelloWorldServiceKey, ctx.self)
      val helloWorldService = ctx.spawnAnonymous(HelloWorld.greeter)
      ctx.watch(helloWorldService)
      Behaviors.receiveMessagePartial[Listing] {
        case HelloWorld.HelloWorldServiceKey.Listing(listings) if listings.nonEmpty =>
          listings.foreach(ps => println(ps))
        Behaviors.same
      } receiveSignal {
        case (_, Terminated(_)) => println("An actor has stopped")
          Behaviors.stopped
      }
    }.narrow
}

object Boot {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[Listing] = ActorSystem(HelloWorldMain.main, "hello")

    // needed in implicit scope for ask (?)
    import akka.actor.typed.scaladsl.AskPattern._
    implicit val ec: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    Thread sleep 200
    val greeter: Future[Listing] =
      system.receptionist ? (ref => Receptionist.Find[Greet](HelloWorld.HelloWorldServiceKey)(ref))

    for (greeterRef ← greeter) {
      greeterRef.serviceInstances(HelloWorld.HelloWorldServiceKey).head ! HelloWorld.Greet("Akka")
    }
    for (greeterRef ← greeter) {
      greeterRef.serviceInstances(HelloWorld.HelloWorldServiceKey).head ! HelloWorld.Greet("Akka")
    }
    for (greeterRef ← greeter) {
      greeterRef.serviceInstances(HelloWorld.HelloWorldServiceKey).head ! HelloWorld.Greet("Akka")
    }
  }
}
