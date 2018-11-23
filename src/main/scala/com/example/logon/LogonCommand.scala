package com.example.logon

import akka.actor.typed.ActorRef
import com.example.logon.SessionRepository.{ Session, SessionId }

trait LogonCommand

trait EnclosedLogonCommand {
  def id: SessionId
}
case class CreateSession(
  id: SessionId,
) extends EnclosedLogonCommand

case class InitiateRemoteAuthentication(
  id: SessionId,
  email: String,
) extends EnclosedLogonCommand

case class LookupSession(
  id: SessionId,
) extends EnclosedLogonCommand


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
case class CommandWithRef(
  enclosedLogonCommand: EnclosedLogonCommand,
  replyTo: ActorRef[Either[Error, Response]]
) extends LogonCommand {
  def id: SessionId = enclosedLogonCommand.id
}

