package com.example.cb

import java.util.UUID

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.headers.HttpCookie
import akka.http.scaladsl.server.Directives
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.example.cb.LoginManager.{ InitiateSession, LoginCommand, Logout, ProvideEmail, Response }
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object Api extends JsonSupport {

  sealed trait ApiCommand

  case object BoundOk extends ApiCommand

  case object BoundError extends ApiCommand

  def apply(config: Config): Behavior[ApiCommand] = {

    Behaviors.setup[ApiCommand] { ctx =>
      implicit val untypedSystem: ActorSystem = ctx.system.toUntyped
      implicit val mat = ActorMaterializer()
      implicit val ec = ctx.executionContext
      implicit val timeout = Timeout(5 seconds)
      implicit val scheduler = ctx.system.scheduler

      val manager = ctx.spawn(LoginManager(config, UserRepository(ec)), "Willem")

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

  val cookieName = "session"

  def route(config: Config, manager: ActorRef[LoginCommand])(implicit askTimeout: Timeout, scheduler: Scheduler) = {
    import Directives._
    path("login") {
      get {
        optionalCookie(cookieName) {
          case Some(cookie) =>
            complete(s"Your session id = ${cookie.value}")
          case None =>
            val sessionId = UUID.randomUUID().toString
            setCookie(HttpCookie("session", value = sessionId)) {
              complete {
                val future: Future[Response] = manager ? (ref => InitiateSession(sessionId, ref))
                future.mapTo[Response]
              }
            }
        }
      }
    } ~
    path("email") {
      parameters('email) { email =>
        cookie(cookieName) { cookie =>
          complete {
            val future: Future[Response] = manager ? (ref => ProvideEmail(cookie.value, email, ref))
            future.mapTo[Response]
          }
        }
      }
    } ~
    path("logout") {
      get {
        cookie(cookieName) { cookie =>
          deleteCookie(cookieName) {
            complete {
              val future: Future[Response] = manager ? (ref => Logout(cookie.value, ref))
              future.mapTo[Response]
            }
          }
        }
      }
    }
  }
}