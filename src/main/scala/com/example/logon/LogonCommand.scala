package com.example.logon

import akka.actor.typed.ActorRef
import com.example.logon.SessionRepository.{ Session, SessionId }

trait LogonCommand

trait SessionCommand extends LogonCommand {
  def id: SessionId
  def replyTo: ActorRef[Either[Error, Response]]
}

case class CreateSession(
  id: SessionId,
  replyTo: ActorRef[Either[Error, Response]]
) extends SessionCommand

case class InitiateRemoteAuthentication(
  id: SessionId,
  email: String,
  replyTo: ActorRef[Either[Error, Response]]
) extends SessionCommand

case class LookupSession(
  id: SessionId,
  replyTo: ActorRef[Either[Error, Response]]
) extends SessionCommand


case class Response(session: Session)

trait Error {
  def message: String
}
case object InvalidSessionid extends Error {
  override def message = "Could not find session ID"
}
case class FailedResult(msg: String) extends Error {
  override def message = "An exception with message $msg"
}

