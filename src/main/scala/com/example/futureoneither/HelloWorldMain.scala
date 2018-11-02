package com.example.futureoneither

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FutureOnEither[L, R](val future: Future[Either[L, R]]) {

  def map[S](f: R => S): FutureOnEither[L, S] =
    new FutureOnEither(
      future.map {
        case Left(l) => Left(l)
        case Right(r) => Right(f(r))
      }
//    new FutureOnEither(
//      future.map {
//        case Left(l) => Left(l)
//        case Right(r) => Right(f(r))
//      }
    )

  def onComplete[U](f: Try[Either[L,R]] => U): Unit = future.onComplete(f)

}

case class SomeError(msg: String)
case class GreatResult(msg: String)

object Boot extends App {

    val fut123: Future[Either[SomeError, GreatResult]] = Future.successful(Right(GreatResult("willem")))
//  val fut123: Future[Either[SomeError, GreatResult]] = Future.successful(Left(SomeError("problem")))
  //  val fut123: Future[Either[SomeError, GreatResult]] = Future.failed(new RuntimeException("asdasd"))
  val foe: FutureOnEither[SomeError, GreatResult] = new FutureOnEither(fut123)
//  val mapped: FutureOnEither[SomeError, String] = foe.map(gr => "123" + gr.msg)
    val mapped: FutureOnEither[SomeError, String] = foe.map(p => "123" + p.msg)
  mapped.onComplete {
    case Success(x) => x match {
      case Left(q) => println(s"Error ${q.msg}")
      case Right(str) => println(s"Success $str")
    }
    case Failure(y) => println("boom");y.printStackTrace()
   }


  Thread sleep 1000
}
