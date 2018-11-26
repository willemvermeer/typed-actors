import Example6.{ Repository, User }
import UserActorV2.Operation
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object UserActorV2 {

  sealed trait Operation
  case class LoadUser(userId: Int, replyTo: ActorRef[Either[String, User]])
    extends Operation
  private case class Loaded(user: User) extends Operation
  private case class DbFailure(str: String) extends Operation

  def userBehavior(repository: Repository): Behavior[Operation] = {

    Behaviors.setup { context =>

      val buffer = StashBuffer[Operation](capacity = 100)

      def main: Behavior[Operation] = Behaviors.receive {
        (context, message) => message match {
          case cmd: LoadUser =>
            import context.executionContext
            repository.getUser(cmd.userId).onComplete {
              case Success(usr) =>
                context.self ! Loaded(usr)
              case Failure(ex) =>
                context.self ! DbFailure(ex.getMessage)
            }
            loading(cmd.replyTo)
        }
      }

      def loading(replyTo: ActorRef[Either[String, User]]): Behavior[Operation] =
        Behaviors.receive {
        (context, message) => message match {
          case loaded: Loaded =>
            replyTo ! Right(loaded.user)
            buffer.unstashAll(context, main)
          case DbFailure(ex) =>
            replyTo ! Left(ex)
            buffer.unstashAll(context, main)
          case other =>
            buffer.stash(other)
            Behaviors.same
        }
      }

      main
    }
  }

}

object Example6 extends App {

  case class User(id: Int, name: String)

  class Repository {
    def getUser(id: Int): Future[User] = Future {
      if (id == 5) User(id, "Sophie")
      else throw new RuntimeException(s"No user for id $id")
    }
  }

  val repo = new Repository()

  val system: ActorSystem[Operation] =
    ActorSystem(UserActorV2.userBehavior(repo), "Example5")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext
  import UserActorV2._

  def loadUser(id: Int) =
    (system ? ((ref: ActorRef[Either[String, User]]) =>
      LoadUser(id, ref))
    ).onComplete {
      case Success(result) => result match {
        case Right(user) =>
          println(s"We have a user $user")
        case Left(ex) =>
          println(s"Failure: $ex")
      }
      case Failure(ex) => println(s"we received an exception $ex")//handle exception, can only be timeout
    }

  loadUser(5)
  loadUser(10)
  // Output:
  // We have a user User(5,Sophie)
  // Failure: No user for id 10

}