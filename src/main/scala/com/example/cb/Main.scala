package com.example.cb

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Terminated }
import com.typesafe.config.Config

object Main {

  class Root(config: Config) extends Actor with ActorLogging {

    val api = context.spawn(Api(config), "CB-Api")

    context.watch(api)
    log.info(s"${context.system.name} up and running")

    def receive = {
      case Terminated(actor) => context.system.terminate()
    }

  }

  def main(args: Array[String]) {

    implicit val system: ActorSystem = ActorSystem("CB-Logon")
    val config = system.settings.config

    system.actorOf(Props(new Root(config)), "root")

  }

}