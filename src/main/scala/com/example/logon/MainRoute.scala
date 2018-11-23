package com.example.logon

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.{ ActorPath, ActorSystem, Scheduler }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.util.Timeout
import com.example.logon.SessionRepository.{ Session, SessionId }

import scala.concurrent.Future
import scala.concurrent.duration._

object MainRoute {
  def apply(system: ActorSystem): MainRoute = new MainRoute(system)
}

class MainRoute(system: ActorSystem) extends Directives {

  import akka.actor.typed.scaladsl.adapter._

  implicit val scheduler: Scheduler = system.toTyped.scheduler // required for ask pattern
  implicit val timeout: Timeout = 2 seconds // required for ask pattern
  implicit val ec = system.dispatcher // required for Futures

  val logonManager = system.spawn(
    LogonManager.behavior(SessionRepository(), UserRepository()),
    "LogonManager")

  val route: Route = concat(
    path("logon") {
      post {
        onSuccess(createSession()) {
          case Right(response) =>
            complete(StatusCodes.OK -> response.session.toString)
          case Left(error) =>
              complete(StatusCodes.InternalServerError -> error.message)
        }
      }
    },
    path("session" / Remaining) { sessionId =>
      get {
        onSuccess(getSession(sessionId)) {
          case Right(response) =>
            complete(StatusCodes.OK -> response.session.toString)
          case Left(error) => error match {
            case InvalidSessionid =>
              complete(StatusCodes.NotFound -> error.message)
            case _ =>
              complete(StatusCodes.InternalServerError -> error.message)
          }
        }
      }
    } ~
    path("session" / Remaining) { sessionId =>
      pathEnd {
        post {
          parameter('email.as[String]) { email => println(s"$email and $sessionId")
            onSuccess(remoteLogon(sessionId, email)) {
              case Right(response) =>
                complete(StatusCodes.OK -> response.session.toString)
              case Left(error) =>
                complete(StatusCodes.InternalServerError -> error.message)
            }
          }
        }
      }
    }
  )

  private def newSessionId(): String = UUID.randomUUID().toString.takeWhile(_ != '-')

  private def createSession(): Future[Either[Error, Response]] =
    logonManager ?
      ((ref: ActorRef[Either[Error, Response]]) =>
        CommandWithRef(CreateSession(newSessionId()), ref))


  private def getSession(id: SessionId): Future[Either[Error, Response]] =
    logonManager ?
      ((ref: ActorRef[Either[Error, Response]]) =>
        CommandWithRef(LookupSession(id), ref))

  private def remoteLogon(id: SessionId, email: String): Future[Either[Error, Response]] =
    logonManager ?
      ((ref: ActorRef[Either[Error, Response]]) =>
        CommandWithRef(InitiateRemoteAuthentication(id, email), ref))

}
