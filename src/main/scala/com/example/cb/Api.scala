package com.example.cb

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives.{ complete, get, pathEndOrSingleSlash }
import akka.stream.ActorMaterializer
import com.example.cb.LoginManager.{ Login, LoginCommand, Response }
import com.typesafe.config.Config
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import akka.util.Timeout
import spray.json.DefaultJsonProtocol

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

object Api extends JsonSupport {

  sealed trait ApiCommand

  case object BoundOk extends ApiCommand

  case object BoundError extends ApiCommand

  def apply(config: Config): Behavior[ApiCommand] = {

    Behaviors.setup[ApiCommand] { ctx =>
      implicit val untypedSystem: ActorSystem = ctx.system.toUntyped
      implicit val mat = ActorMaterializer()
      import ctx.executionContext
      implicit val timeout = Timeout(5 seconds)
      implicit val scheduler = ctx.system.scheduler

      val manager = ctx.spawn(LoginManager(config), "Willem")

      val bindingFuture =
        Http()
          .bindAndHandle(route(config, manager)(timeout, scheduler), "localhost", 8080)
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

  def route(config: Config, manager: ActorRef[LoginCommand])(implicit askTimeout: Timeout, scheduler: Scheduler) = {
    pathEndOrSingleSlash {
      get {
        //        complete(Response("willem"))
        complete {
          val future: Future[Response] = manager ? (ref => Login("123", ref))
          future.mapTo[Response]
        }
      }
    }
  }
}