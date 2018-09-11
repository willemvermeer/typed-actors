package com.example.cb

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.example.cb.LoginManager.{ Login, LoginCommand, Response }
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
  case class Logout(id: String, ref: ActorRef[Response]) extends LoginCommand

  case class Response(txt: String)

  def apply(config: Config, sessions: Map[String, ActorRef[LoginCommand]] = Map.empty): Behavior[LoginCommand] = {
    Behaviors.setup[LoginCommand] { context =>
      Behaviors.receiveMessage[LoginCommand] {
        case command: Login =>
          sessions.get(command.id) match {
            case Some(s) =>
              s ! command
              Behaviors.same
            case None =>
              val handler = context.spawn(LoginHandler.apply(config, command.id), command.id)
              handler ! command
              LoginManager(config, sessions + (command.id -> handler))
          }
      }
    }
  }

}

object LoginHandler {
  def apply(config: Config, id: String): Behavior[LoginCommand] = {
    Behaviors.receiveMessage[LoginCommand] {
      case command: Login => println(s"Hello login ${id}")
        command.ref ! Response("hoi")
        Behaviors.same
    }
  }
}