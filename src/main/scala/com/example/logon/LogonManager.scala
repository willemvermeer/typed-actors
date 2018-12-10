package com.example.logon

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.util.Timeout
import com.example.logon.SessionRepository.SessionId

import scala.concurrent.duration._

object LogonManager {

  def name = "LogonManager"

  def behavior(sessionRepository: SessionRepository,
               userRepository: UserRepository
  ): Behavior[LogonCommand] = Behaviors.setup { context =>
    implicit val timeout: Timeout = 3.seconds
    implicit val scheduler: Scheduler = context.system.scheduler

    def getOrSpawnChild(id: SessionId):
      ActorRef[LogonCommand] = {
      val childName = LogonHandler.name(id)
      context.child(childName) match {
        case Some(childActor) => childActor.unsafeUpcast
        case None => context.spawn[LogonCommand](
          LogonHandler.behavior(
            id, sessionRepository, userRepository),
          childName)
      }
    }

    Behaviors.receiveMessage[LogonCommand] {
      case command: SessionCommand =>
        val logonHandler = getOrSpawnChild(command.id)
        logonHandler ! command
        Behaviors.same
    }
  }

}