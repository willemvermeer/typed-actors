package com.example.logon

import com.example.logon.SessionRepository.{ Session, SessionId }

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object SessionRepository {
  def apply(): SessionRepository = new SessionRepository()

  type SessionId = String

  case class Session(id: SessionId, email: Option[String] = None, status: SessionStatus.Value = SessionStatus.NEW) {
    override def toString = s"Session with id $id status $status for email $email"
  }

  object SessionStatus extends Enumeration {
    val NEW, PENDING, SUCCESS, FAILED = Value
  }

}

class SessionRepository {

  var sessions = mutable.Map[SessionId, Session]()

  def save(session: Session): Future[Session] =
    Future {
      sessions(session.id) = session
      session
    }

  def get(id: String): Future[Option[Session]] =
    Future {
      sessions.get(id)
    }

}