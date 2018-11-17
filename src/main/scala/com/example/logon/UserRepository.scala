package com.example.logon

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object UserRepository {

  def apply(): UserRepository = new UserRepository()

  case class User(id: Int, name: String)

}

class UserRepository {
  import UserRepository.User

  def findByEmail(email: String): Future[Option[User]] =
    Future {
      if (email == "test@email.com")
        Some(User(7, "test@email.com"))
      else
        None
    }

}