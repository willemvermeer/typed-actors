package com.example.logon

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn

object Boot {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("LogonSystem")
    val mainRoute = MainRoute(system)

    implicit val mat = ActorMaterializer()
    implicit val ec = system.getDispatcher

    val bindingFuture = Http().bindAndHandle(
      mainRoute.route, "localhost", 8080)

    StdIn.readLine() // let it run until user presses return

    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}