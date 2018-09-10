package com.example.cb

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.example.cb.LoginManager.{ LoginCommand, Response }
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

  case class Response(txt: String)

  def apply(config: Config): Behavior[LoginCommand] = {
    Behaviors.setup[LoginCommand] { context =>

      Behaviors.receiveMessage[LoginCommand] {
        case command: Login =>
          val msg = s"Starting session for ${command.id}"
          println(msg)
          command.ref ! Response(msg)
          Behaviors.same
      }
    }
  }

}

object LoginHandler {
  def apply(config: Config, id: String): Behavior[LoginCommand] = {
    Behaviors.same
  }
}