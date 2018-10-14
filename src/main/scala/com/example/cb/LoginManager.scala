package com.example.cb

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior, Terminated }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.example.cb.LoginManager.{ InitiateSession, LoginCommand, Logout, ProvideEmail, Response }
import com.typesafe.config.Config
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val responseFormat = jsonFormat1(Response)
}

object LoginManager extends JsonSupport {

  sealed trait LoginCommand {
    def id: String
  }
  case class InitiateSession(id: String, ref: ActorRef[Response]) extends LoginCommand
  case class ProvideEmail(id: String, email: String, ref: ActorRef[Response]) extends LoginCommand
  case class Logout(id: String, ref: ActorRef[Response]) extends LoginCommand

  case class Response(txt: String)

  def apply(config: Config, userRepository: UserRepository): Behavior[LoginCommand] = {
    Behaviors.setup[LoginCommand] { context =>
      println(s"Applying to ${context.children.size} elements")
      Behaviors.receiveMessage[LoginCommand] {
        case command: InitiateSession =>
          context.child(command.id) match {
            case Some(s) =>
              println(s"Found a child matching ${command.id}")
              s.upcast[LoginCommand] ! command
              Behaviors.same
            case None =>
              println(s"Spawning new child for ${command.id}")
              val handler = context.spawn(LoginHandler.apply(config, Session(command.id), userRepository), command.id)
              context.watch(handler)
              handler ! command
              Behaviors.same
          }
        case command: ProvideEmail => context.child(command.id) match {
          case Some(s) =>
            println(s"Providing email ${command.email} to session ${command.id}")
            s.upcast[LoginCommand] ! command
            Behaviors.same
          case None =>
            println(s"No child matching ${command.id} for logout")
            command.ref ! Response("No such session")
            Behaviors.same
        }
        case command: Logout =>
          context.child(command.id) match {
            case Some(s) =>
              println(s"Found a child matching ${command.id} for logout")
              s.asInstanceOf[ActorRef[LoginCommand]] ! command
              Behaviors.same
            case None =>
              println(s"No child matching ${command.id} for logout")
              command.ref ! Response("No such session")
              Behaviors.same
          }
      }.receiveSignal {
        case (_, Terminated(ref)) =>
          println(s"$ref has terminated")
          Behaviors.same
      }
    }
  }

}

case class Session(id: String, email: Option[String] = None, userId: Option[Long] = None)

object LoginHandler {
  def apply(config: Config, session: Session, userRepository: UserRepository): Behavior[LoginCommand] = {
    Behaviors.setup { ctx =>
      Behaviors.receiveMessage[LoginCommand] {
        //      println(s"Current session status $session")
        case command: InitiateSession => println(s"Hello login ${session.id}")
          command.ref ! Response(s"Hoi session ${session.id}")
          Behaviors.same
        case command: ProvideEmail =>
          import ctx.executionContext
          // do a time consuming operation
          userRepository.findByEmail(command.email).onComplete {
            case Success(l) => command.ref ! Response("Your userId = $l")
              LoginHandler(config, session.copy(email = Some(command.email), userId = Some(l)), userRepository)
            case Failure(exception) =>
              command.ref ! Response("Invalid email")
          }
          Behaviors.same
        case command: Logout =>
          println("Logging out")
          command.ref ! Response(s"Bye bye session ${session.id}")
          Behaviors.stopped
      }
    }
  }
}