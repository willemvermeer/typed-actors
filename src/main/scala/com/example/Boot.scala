package com.example

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

import scala.concurrent.ExecutionContextExecutor
import scala.io.StdIn

object Boot {

  def main(args: Array[String]) {

    implicit val system = ActorSystem(Behavior.empty[Any], "empty-system")
    implicit val executionContext: ExecutionContextExecutor = system.executionContext

    val route =
      path("hello") {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      }

    val loggedRoute = RequestLogger(Logging.InfoLevel, route)

    // the following line is required for Http to correctly start (otherwise weird compilation error)
    implicit val untypedSystem = system.toUntyped
    implicit val mat = ActorMaterializer()
    val bindingFuture = Http().bindAndHandle(loggedRoute, "localhost", 8080)

    system.log.info(s"Server online at http://localhost:8080/\nPress RETURN to stop...")

    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }

}