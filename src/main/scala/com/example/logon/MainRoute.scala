package com.example.logon

import java.util.UUID

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.{ ActorSystem, Scheduler }
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{ Directives, Route }
import akka.util.Timeout
import com.example.logon.SessionRepository.{ Session, SessionId }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object MainRoute {
  def apply(system: ActorSystem): MainRoute = new MainRoute(system)
}

class MainRoute(system: ActorSystem) extends Directives {

  import akka.actor.typed.scaladsl.adapter._

  implicit val scheduler: Scheduler = system.toTyped.scheduler // required for ask pattern
  implicit val timeout: Timeout = 2 seconds // required for ask pattern
  implicit val ec = system.dispatcher // required for Futures

  // TODO: nog uitzoeken hoe we in core een unieke manager actor aanmaken
  val logonManager = system.spawn(
    LogonManager.behavior(SessionRepository(), UserRepository()),
    "LogonManager")

  val route: Route = concat(
    path("logon") {
      post {
        onComplete(createSession()) {
          case Success(session) =>
            complete(StatusCodes.OK -> session.toString())
          case Failure(ex) =>
            complete(StatusCodes.InternalServerError -> ex.getMessage)
        }
      }
    },
    path("session" / Remaining) { sessionId =>
      get {
        onComplete(getSession(sessionId)) {
          case Success(session) =>
            complete(StatusCodes.OK -> session.toString())
          case Failure(ex) =>
            complete(StatusCodes.InternalServerError -> ex.getMessage)
        }
      }
    } ~
    path("session" / Remaining) { sessionId =>
      pathEnd {
        post {
          parameter('email.as[String]) { email => println(s"$email and $sessionId")
            onComplete(remoteLogon(sessionId, email)) {
              case Success(session) =>
                complete(StatusCodes.OK -> session.toString())
              case Failure(ex) =>
                complete(StatusCodes.InternalServerError -> ex.getMessage)
            }
          }
        }
      }
    }
  )

  private def newSessionId(): String = UUID.randomUUID().toString.takeWhile(_ != '-')

  private def createSession(): Future[Option[Session]] =
    for {
      response <- logonManager ?
        ((ref: ActorRef[Response]) =>
          CommandWithRef(CreateSession(newSessionId()), ref))
    } yield response.session


  private def getSession(id: SessionId): Future[Option[Session]] =
    for {
      response <- logonManager ?
        ((ref: ActorRef[Response]) =>
          CommandWithRef(LookupSession(id), ref))
    } yield response.session

  private def remoteLogon(id: SessionId, email: String): Future[Option[Session]] =
    for {
      response <- logonManager ?
        ((ref: ActorRef[Response]) =>
          CommandWithRef(InitiateRemoteAuthentication(id, email), ref))
    } yield response.session

}
