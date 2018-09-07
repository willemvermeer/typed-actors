package com.example

import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, DispatcherSelector, Terminated }
import akka.event.Logging.{ Debug, LogLevel }
import akka.event.{ Logging, LoggingAdapter }
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.directives.{ DebuggingDirectives, LogEntry, LoggingMagnet }
import akka.stream.Materializer

object RequestLogger {

  def apply(logLevel: LogLevel, route: Route)(implicit m: Materializer): Route = {

    def simpleLogger(loggingAdapter: LoggingAdapter, reqTimestamp: Long)(request: HttpRequest)(response: Any): Unit = {
      val duration = System.currentTimeMillis() - reqTimestamp
      val entry = response match {
        case Complete(resp) => LogEntry(s"${request.method.name} ${request.uri.path} ${resp.status} $duration ms.", logLevel)
        case other => LogEntry(s"${request.method.name} ${request.uri.path} $other $duration ms.", Logging.ErrorLevel)
      }
      entry.logTo(loggingAdapter)
    }

    DebuggingDirectives.logRequestResult(LoggingMagnet(log => {
      val requestTimestamp = System.currentTimeMillis()
      simpleLogger(log, requestTimestamp)
    }))(route)

  }

}
