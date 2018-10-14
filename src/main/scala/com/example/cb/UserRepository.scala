package com.example.cb

import scala.concurrent.{ ExecutionContext, Future }

object UserRepository {
  def apply(ec: ExecutionContext): UserRepository = {
    new UserRepository()(ec)
  }
}

class UserRepository()(implicit ec: ExecutionContext) {

  def findByEmail(email: String): Future[Long] =
    Future {
      Thread sleep 500
      if (email == "test@email.com")
        42L
      else
        throw new RuntimeException("No user for $email")
    }

}