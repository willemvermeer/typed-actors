package com.example.logon

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.io.StdIn

object Boot {

  def main(args: Array[String]) {

    implicit val system = ActorSystem("LogonSystem")
    implicit val ec = system.getDispatcher

    val mainRoute = MainRoute(system)

    // the following line is required for Http to correctly start (otherwise weird compilation error)
    implicit val mat = ActorMaterializer()
    val bindingFuture = Http().bindAndHandle(mainRoute.route, "localhost", 8080)

    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}