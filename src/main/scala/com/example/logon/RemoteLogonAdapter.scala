package com.example.logon

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Random

class RemoteLogonAdapter()(implicit val ec: ExecutionContext) {
  def remoteLogon(email: String): Future[Boolean] = {
    Future {
      Thread sleep 500
      Random.nextBoolean()
    }
  }
}
