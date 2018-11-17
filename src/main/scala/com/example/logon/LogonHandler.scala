package com.example.logon

import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior }
import com.example.logon.SessionRepository.{ Session, SessionId, SessionStatus }

import scala.language.implicitConversions
import scala.util.{ Success, Try }

object LogonHandler {

  def name(id: SessionId): String = s"LogonHandler-${id.toString}"

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
               userRepository: UserRepository): Behavior[LogonCommand] =
    Behaviors.setup { context =>
      import context.executionContext
      val buffer = StashBuffer[LogonCommand](capacity = 100)
      val remoteLogonAdapter =
        new RemoteLogonAdapter()(context.executionContext)

      def init(): Behavior[LogonCommand] = Behaviors.receive[LogonCommand] { (ctx, msg) =>
        msg match {
          case Start(pa) => println(s"Start $pa")
            buffer.unstashAll(ctx, active(pa))
          case DBError(ex) => println(s"DBError $ex")
            throw DBException(ex.getMessage)
          case x => println(s"Stash $x")
            buffer.stash(x)
            Behaviors.same
        }
      }

      def active(pa: Option[Session]): Behavior[LogonCommand] = Behaviors.receive { (ctx, msg) =>
        msg match {
          case CommandWithRef(command: CreateSession, replyTo) => pa match {
            case None =>
              val state = Session(id = command.id)
              sessionRepository.save(state).onComplete {
                case Success(savedPA) =>
                  ctx.self ! SaveSuccess(savedPA)
                case scala.util.Failure(cause) =>
                  ctx.self ! DBError(cause)
              }
              saving(replyTo)

            case Some(state) =>
              replyTo ! Response(Some(state))
              Behaviors.stopped
          }

          case CommandWithRef(command: InitiateRemoteAuthentication, replyTo) => pa match {
            case None =>
              replyTo ! Response(None)
              Behaviors.stopped
            case Some(state) =>
              remoteLogonAdapter.remoteLogon(command.email).onComplete {
                case Success(result) =>
                  val finalStatus = if (result) SessionStatus.SUCCESS else SessionStatus.FAILED
                  ctx.self ! RemoteAuthenticationSuccess(finalStatus, command.email)
                case scala.util.Failure(cause) =>
                  ctx.self ! DBError(cause)
              }
              initiatingRemoteAuthentication(state, replyTo)
          }

          case CommandWithRef(command: LookupSession, replyTo) => pa match {
            case None => println(s"lookup but is not ")
              replyTo ! Response(None)
              Behaviors.same
            case Some(state) =>
              replyTo ! Response(Some(state))
              Behaviors.same
          }
        }
      }

      def saving(replyTo: ActorRef[Response]): Behavior[LogonCommand] =
        Behaviors.receive[LogonCommand] { (ctx, msg) =>
          msg match {
            case SaveSuccess(pa) =>
              replyTo ! Response(Some(pa))
              buffer.unstashAll(ctx, active(Some(pa)))
              active(Some(pa))
            case DBError(ex) => throw ex
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

      def initiatingRemoteAuthentication(session: Session, replyTo: ActorRef[Response]): Behavior[LogonCommand] = {
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
        case Success(pa) => println(s"SUccess $pa")
          context.self ! Start(pa)
        case scala.util.Failure(ex) => println(s"Fail $ex")
          context.self ! DBError(ex)
      }

      init()

    }
}