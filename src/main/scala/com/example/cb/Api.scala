package com.example.cb

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives.{ complete, get, pathEndOrSingleSlash }
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

import scala.util.{ Failure, Success }

object Api {

  sealed trait ApiCommand
  case object BoundOk extends ApiCommand
  case object BoundError extends ApiCommand

  def apply(config: Config): Behavior[ApiCommand] = {

    Behaviors.setup[ApiCommand] { ctx =>
      implicit val untypedSystem: ActorSystem = ctx.system.toUntyped
      implicit val mat = ActorMaterializer()
      import ctx.executionContext
      implicit val scheduler = ctx.system.scheduler

      val bindingFuture =
        Http()
        .bindAndHandle(route(config)(scheduler), "localhost", 8080)
        .onComplete {
          case Success(ServerBinding(address)) =>
            untypedSystem.log.info(s"Server online at http://localhost:8080/ ${address}")
            ctx.self ! BoundOk
          case Failure(ex) =>
            untypedSystem.log.info(s"Exception while starting $ex")
            ctx.self ! BoundError
        }

      Behaviors.receiveMessage[ApiCommand] {
        case BoundError =>
          untypedSystem.log.info(s"Exception while starting")
          Behaviors.stopped
        case BoundOk => Behaviors.ignore
      }
    }
  }

  def route(config: Config)(implicit scheduler: Scheduler) = {
    pathEndOrSingleSlash {
      get {
        complete {
          // TODO: invoke some actor
          "OK"
        }
      }
    }
  }
}
