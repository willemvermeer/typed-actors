package com.example.logon

import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior }
import com.example.logon.SessionRepository.{ Session, SessionId, SessionStatus }

import scala.language.implicitConversions
import scala.util.Success

object LogonHandler {

  def name(id: SessionId): String =
    s"LogonHandler-${id.toString}"

  // internal incoming message types
  trait InternalLogonCommand extends LogonCommand

  private case class Start(
    session: Option[Session]
  ) extends InternalLogonCommand

  private case class DBError(
    ex: Throwable
  ) extends InternalLogonCommand

  private case class SaveSuccess(
    session: Session
  ) extends InternalLogonCommand

  private case class RemoteAuthenticationSuccess(
    status: SessionStatus.Value,
    email: String
  ) extends InternalLogonCommand

  case class DBException(msg: String) extends RuntimeException

  def behavior(id: SessionId,
               sessionRepository: SessionRepository,
               userRepository: UserRepository
  ): Behavior[LogonCommand] =
    Behaviors.setup { context =>
      val buffer = StashBuffer[LogonCommand](capacity = 10)
      val remoteLogonAdapter =
        new RemoteLogonAdapter()(context.executionContext)

      def init(): Behavior[LogonCommand] =
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
        msg match {
          case Start(session) =>
            buffer.unstashAll(ctx, active(session))
          case DBError(ex) =>
            buffer.unstashAll(ctx, dbError(ex))
          case x =>
            buffer.stash(x)
            Behaviors.same
        }
      }

      def dbError(ex: Throwable): Behavior[LogonCommand] =
        Behaviors.receiveMessage {
        case message: SessionCommand =>
          message.replyTo ! Left(FailedResult(ex.getMessage))
          Behaviors.stopped
        case _ => throw ex
      }

      def active(session: Option[Session]):  Behavior[LogonCommand] =
        Behaviors.receive { (ctx, msg) =>
        msg match {
          case command: CreateSession => session match {
            case None =>
              val newSession = Session(id = command.id)
              context.pipeToSelf(sessionRepository.save(newSession)) {
                case Success(savedSession) =>
                  SaveSuccess(savedSession)
                case scala.util.Failure(cause) =>
                  DBError(cause)
              }
              saving(command.replyTo)

            case Some(s) =>
              command.replyTo ! Right(Response(s))
              Behaviors.stopped
          }

          case command: InitiateRemoteAuthentication => session match {
            case None =>
              command.replyTo ! Left(InvalidSessionid)
              Behaviors.stopped
            case Some(state) =>
              context.pipeToSelf(remoteLogonAdapter.remoteLogon(command.email)) {
                case Success(result) =>
                  val finalStatus = if (result) SessionStatus.SUCCESS else SessionStatus.FAILED
                  RemoteAuthenticationSuccess(finalStatus, command.email)
                case scala.util.Failure(cause) =>
                  DBError(cause)
              }
              initiatingRemoteAuthentication(state, command.replyTo)
          }

          case command: LookupSession => session match {
            case None =>
              command.replyTo ! Left(InvalidSessionid)
              Behaviors.same
            case Some(state) =>
              command.replyTo ! Right(Response(state))
              Behaviors.same
          }
        }
      }

      def saving(replyTo: ActorRef[Either[Error, Response]]): Behavior[LogonCommand] =
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
          msg match {
            case SaveSuccess(session) =>
              replyTo ! Right(Response(session))
              buffer.unstashAll(ctx, active(Some(session)))
            case DBError(ex) =>
              replyTo ! Left(FailedResult(ex.getMessage))
              Behaviors.stopped
            case x =>
              buffer.stash(x)
              Behaviors.same
          }
        }

      def initiatingRemoteAuthentication(session: Session,
        replyTo: ActorRef[Either[Error, Response]]): Behavior[LogonCommand] = {
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
          msg match {
            case RemoteAuthenticationSuccess(status, email) =>
              val newState = session.copy(status = status, email = Some(email))
              context.pipeToSelf(sessionRepository.save(newState)) {
                case Success(savedSession) =>
                  SaveSuccess(savedSession)
                case scala.util.Failure(cause) =>
                  DBError(cause)
              }
              saving(replyTo)
            case _ =>
              throw DBException("Failure or unknown message after checking base")
          }
        }
      }

      // first try to load session from the database
      context.pipeToSelf(sessionRepository.get(id)) {
        case Success(session) =>
          Start(session)
        case scala.util.Failure(ex) =>
          DBError(ex)
      }

      init()

    }
}