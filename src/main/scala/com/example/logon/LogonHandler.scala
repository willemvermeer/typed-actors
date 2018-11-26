package com.example.logon

import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior }
import com.example.logon.SessionRepository.{ Session, SessionId, SessionStatus }

import scala.language.implicitConversions
import scala.util.{ Success, Try }

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
      import context.executionContext
      val buffer = StashBuffer[LogonCommand](capacity = 10)
      val remoteLogonAdapter =
        new RemoteLogonAdapter()(context.executionContext)

      def init(): Behavior[LogonCommand] =
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
        msg match {
          case Start(pa) =>
            buffer.unstashAll(ctx, active(pa))
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

      def active(pa: Option[Session]): Behavior[LogonCommand] =
        Behaviors.receive { (ctx, msg) =>
        msg match {
          case command: CreateSession => pa match {
            case None =>
              val state = Session(id = command.id)
              sessionRepository.save(state).onComplete {
                case Success(savedPA) =>
                  ctx.self ! SaveSuccess(savedPA)
                case scala.util.Failure(cause) =>
                  ctx.self ! DBError(cause)
              }
              saving(command.replyTo)

            case Some(state) =>
              command.replyTo ! Right(Response(state))
              Behaviors.stopped
          }

          case command: InitiateRemoteAuthentication => pa match {
            case None =>
              command.replyTo ! Left(InvalidSessionid)
              Behaviors.stopped
            case Some(state) =>
              remoteLogonAdapter.remoteLogon(command.email).onComplete {
                case Success(result) =>
                  val finalStatus = if (result) SessionStatus.SUCCESS else SessionStatus.FAILED
                  ctx.self ! RemoteAuthenticationSuccess(finalStatus, command.email)
                case scala.util.Failure(cause) =>
                  ctx.self ! DBError(cause)
              }
              initiatingRemoteAuthentication(state, command.replyTo)
          }

          case command: LookupSession => pa match {
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
            case SaveSuccess(pa) =>
              replyTo ! Right(Response(pa))
              buffer.unstashAll(ctx, active(Some(pa)))
              active(Some(pa))
            case DBError(ex) =>
              replyTo ! Left(FailedResult(ex.getMessage))
              active(None)
            case x =>
              buffer.stash(x)
              Behaviors.same
          }
        }

      def handleSaveResult(self: ActorRef[LogonCommand]): Try[Session] => Unit = {
        case Success(savedPA) =>
          self ! SaveSuccess(savedPA)
        case scala.util.Failure(cause) =>
          self ! DBError(cause)
      }

      def initiatingRemoteAuthentication(session: Session,
        replyTo: ActorRef[Either[Error, Response]]): Behavior[LogonCommand] = {
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
          msg match {
            case RemoteAuthenticationSuccess(status, email) =>
              val newState = session.copy(status = status, email = Some(email))
              sessionRepository.save(newState).onComplete(handleSaveResult(ctx.self))
              saving(replyTo)
            case _ =>
              throw DBException("Failure or unknown message after checking base")
          }
        }
      }

      // first try to load the pending authentication from the database
      sessionRepository.get(id).onComplete {
        case Success(pa) =>
          context.self ! Start(pa)
        case scala.util.Failure(ex) =>
          context.self ! DBError(ex)
      }

      init()

    }
}