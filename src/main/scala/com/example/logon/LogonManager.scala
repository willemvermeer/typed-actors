package com.example.logon

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.util.Timeout
import com.example.logon.SessionRepository.SessionId

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object LogonManager {

  def name = "LogonManager"

  def behavior(sessionRepository: SessionRepository, userRepository: UserRepository): Behavior[LogonCommand] = Behaviors.setup { context =>
    implicit val timeout: Timeout = 3.seconds
    import context.executionContext
    implicit val scheduler: Scheduler = context.system.scheduler

    def getOrSpawnChild(id: SessionId): ActorRef[LogonCommand] = {
      val childName = LogonHandler.name(id)
      context.child(childName) match {
        case Some(childActor) => childActor.upcast
        case None => context.spawn[LogonCommand](
          LogonHandler.behavior(id, sessionRepository, userRepository),
          childName)
      }
    }

    Behaviors.receiveMessage[LogonCommand] {
      case command: CommandWithRef =>
        val logonHandler = getOrSpawnChild(command.id)
        val result: Future[Either[Error, Response]] =
          logonHandler ?
            (ref => CommandWithRef(command.enclosedLogonCommand, ref))
        result.onComplete {
          case Success(success) => command.replyTo ! success
          case Failure(ex) => command.replyTo ! Left(FailedResult(ex.getMessage))
        }
        Behaviors.same
    }
  }
}