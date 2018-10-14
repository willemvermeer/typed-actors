package com.example.spawningmodified

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, Props, SpawnProtocol }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

/**
  * Adapted copy of SpawnProtocol example on https://doc.akka.io/docs/akka/current/typed/actor-lifecycle.html
  */
object HelloWorldMain {
  val main: Behavior[GetOrSpawnProtocol] =
    Behaviors.setup { ctx ⇒
      GetOrSpawnProtocol.behavior
    }
}

object Boot {

  def main(args: Array[String]): Unit = {
    val system: ActorSystem[GetOrSpawnProtocol] = ActorSystem(HelloWorldMain.main, "hello")

    // needed in implicit scope for ask (?)
    import akka.actor.typed.scaladsl.AskPattern._
    implicit val ec: ExecutionContext = system.executionContext
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = system.scheduler

    val greeter: Future[ActorRef[HelloWorld.Greet]] =
      system ? GetOrSpawnProtocol.Spawn(behavior = HelloWorld.greeter(new UserRepository()), name = "greeter")

    val greetedBehavior = Behaviors.receive[HelloWorld.Greeted] { (ctx, msg) ⇒
      ctx.log.info("Greeting for {} from {}", msg.whom, msg.from)
      Behaviors.stopped
    }

    val greetedReplyTo: Future[ActorRef[HelloWorld.Greeted]] =
      system ? GetOrSpawnProtocol.Spawn(greetedBehavior, name = "willem")

    for (greeterRef ← greeter; replyToRef ← greetedReplyTo) {
      greeterRef ! HelloWorld.Greet("Akka", replyToRef)
    }

    val greeter2: Future[ActorRef[HelloWorld.Greet]] =
      system ? GetOrSpawnProtocol.Spawn(behavior = HelloWorld.greeter(new UserRepository()), name = "greeter")

    for (greeterRef ← greeter2; replyToRef ← greetedReplyTo) {
      greeterRef ! HelloWorld.Greet("Akka2", replyToRef)
    }

  }
}

object HelloWorld {
  final case class Greet(whom: String, replyTo: ActorRef[Greeted])
  final case class Greeted(whom: String, from: ActorRef[Greet])

  def greeter(userRepo: UserRepository): Behavior[Greet] = Behaviors.receive { (ctx, msg) ⇒
    ctx.log.info("Hello {}!", msg.whom)
    ctx.log.info("User {}!", userRepo.user(42))

    msg.replyTo ! Greeted(msg.whom, ctx.self)
    Behaviors.same
  }
}

class UserRepository {
  def user(id: Int) = User(id)
}
case class User(id: Int)

object GetOrSpawnProtocol {

  object Spawn {
    /**
      * Special factory to make using Spawn with ask easier. Props defaults to Props.empty
      */
    def apply[T](behavior: Behavior[T], name: String): ActorRef[ActorRef[T]] ⇒ Spawn[T] =
      replyTo ⇒ new Spawn(behavior, name, Props.empty, replyTo)
  }

  /**
    * Get or spawn a child actor with the given `behavior` and send back the `ActorRef` of that child to the given
    * `replyTo` destination.
    *
    * In case an actor with 'name' already exists, return that one; otherwise spawn a new one
    */
  final case class Spawn[T](behavior: Behavior[T], name: String, props: Props, replyTo: ActorRef[ActorRef[T]])
    extends GetOrSpawnProtocol

  /**
    * Behavior implementing the [[SpawnProtocol]].
    */
  val behavior: Behavior[GetOrSpawnProtocol] =
    Behaviors.receive { (ctx, msg) ⇒
      msg match {
        case Spawn(bhvr, name, props, replyTo) ⇒
          ctx.log.info("Logging all children")
          ctx.log.info(s"Name=$name")

          ctx.children.foreach(c => ctx.log.info(s"$c"))
          val ref =
            ctx.child(name) match {
              case Some(a) => ctx.log.info("Actor already exists");a
              case None => ctx.log.info("Creating fresh actor");ctx.spawn(bhvr, name, props)
            }
          replyTo ! ref.upcast
          Behaviors.same
      }
    }

}

sealed abstract class GetOrSpawnProtocol