package com.example.logon

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.util.Timeout
import com.example.logon.SessionRepository.SessionId

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

  val logonManager: ActorRef[LogonCommand] = system.spawn(
    LogonManager.behavior(SessionRepository(), UserRepository()),
    "LogonManager")

  val route: Route = concat(
    path("logon") {
      post {
        createSession()
      }
    },
    path("session" / Remaining) { sessionId =>
      get {
        getSession(sessionId)
      }
    } ~
    path("session" / Remaining) { sessionId =>
      pathEnd {
        post {
          parameter('email.as[String]) { email =>
            remoteLogon(sessionId, email)
          }
        }
      }
    }
  )

  private def newSessionId(): String = UUID.randomUUID().toString.takeWhile(_ != '-')

  private def createSession(): Route = {
    val future: Future[Either[Error, Response]] =
      logonManager ?
        ((ref: ActorRef[Either[Error, Response]]) =>
          CreateSession(newSessionId(), ref))
    onSuccess(future) {
      case Right(response) =>
        complete(StatusCodes.OK ->
          response.session.toString)
      case Left(error) =>
        complete(StatusCodes.InternalServerError ->
          error.message)
    }
  }


  private def getSession(id: SessionId) = {
    val future = logonManager ?
      ((ref: ActorRef[Either[Error, Response]]) =>
        LookupSession(id, ref))
    onSuccess(future) {
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

  private def remoteLogon(id: SessionId, email: String) = {
    val future = logonManager ?
      ((ref: ActorRef[Either[Error, Response]]) =>
        InitiateRemoteAuthentication(id, email, ref))
    onSuccess(future) {
      case Right(response) =>
        complete(StatusCodes.OK -> response.session.toString)
      case Left(error) =>
        complete(StatusCodes.InternalServerError -> error.message)
    }
  }

}
