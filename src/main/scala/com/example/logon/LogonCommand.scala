package com.example.logon

import akka.actor.typed.ActorRef
import com.example.logon.SessionRepository.{ Session, SessionId }

trait LogonCommand

trait EnclosedLogonCommand {
  def id: SessionId
}

case class Response(session: Option[Session])

case class CommandWithRef(
  enclosedLogonCommand: EnclosedLogonCommand,
  replyTo: ActorRef[Response]
) extends LogonCommand {
  def id: SessionId = enclosedLogonCommand.id
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
