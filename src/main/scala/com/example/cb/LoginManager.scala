package com.example.cb

import akka.actor.typed.{ ActorRef, Behavior, Terminated }
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.example.cb.LoginManager.{ Login, LoginCommand, Logout, Response }
import com.typesafe.config.Config
import spray.json.DefaultJsonProtocol

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val responseFormat = jsonFormat1(Response)
}

object LoginManager extends JsonSupport {

  sealed trait LoginCommand {
    def id: String
  }
  case class Login(id: String, ref: ActorRef[Response]) extends LoginCommand
  case class KeepAlive(id: String, ref: ActorRef[Response]) extends LoginCommand
  case class Logout(id: String, ref: ActorRef[Response]) extends LoginCommand

  case class Response(txt: String)

  def apply(config: Config): Behavior[LoginCommand] = {
    Behaviors.setup[LoginCommand] { context =>
      println(s"Applying to ${context.children.size} elements")
      Behaviors.receiveMessage[LoginCommand] {
        case command: Login =>
          context.child(command.id) match {
            case Some(s) =>
              println(s"Found a child matching ${command.id}")
              s.upcast[LoginCommand] ! command
              Behaviors.same
            case None =>
              println(s"Spawning new child for ${command.id}")
              val handler = context.spawn(LoginHandler.apply(config, command.id), command.id)
              context.watch(handler)
              handler ! command
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
          LoginManager(config)
      }
    }
  }

}

object LoginHandler {
  def apply(config: Config, id: String): Behavior[LoginCommand] = {
    Behaviors.receiveMessage[LoginCommand] {
      case command: Login => println(s"Hello login ${id}")
        command.ref ! Response(s"Hoi session $id")
        Behaviors.same
      case command: Logout =>
        println("Logging out")
        command.ref ! Response(s"Bye bye session $id")
        Behaviors.stopped
    }
  }
}