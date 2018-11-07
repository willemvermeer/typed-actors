import Example5.{ Repository, User }
import UserActor.Operation
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object UserActor {

  sealed trait Operation
  case class LoadUser(userId: Int, replyTo: ActorRef[User]) extends Operation
  private case class Loaded(user: User) extends Operation

  def userBehavior(repository: Repository): Behavior[Operation] = {

    Behaviors.setup { context =>

      val buffer = StashBuffer[Operation](capacity = 100)

      def main: Behavior[Operation] = Behaviors.receive {
        (context, message) => message match {
          case cmd: LoadUser =>
            import context.executionContext
            repository.getUser(cmd.userId).onComplete {
              case Success(usr) => context.self ! Loaded(usr)
            }
            loading(cmd.replyTo)
        }
      }

      def loading(replyTo: ActorRef[User]): Behavior[Operation] = Behaviors.receive {
        (context, message) => message match {
          case loaded: Loaded =>
            replyTo ! loaded.user
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

object Example5 extends App {

  case class User(id: Int, name: String)

  class Repository {
    def getUser(id: Int): Future[User] = Future {
      User(id, "Sophie")
    }
  }

  val repo = new Repository()

  val system: ActorSystem[Operation] =
    ActorSystem(UserActor.userBehavior(repo), "Example5")

  implicit val timeout: Timeout = 5 seconds
  implicit val scheduler: Scheduler = system.scheduler
  implicit val ec: ExecutionContextExecutor = system.executionContext
  import UserActor._

  (system ? ((ref: ActorRef[User]) => LoadUser(5, ref)))
  .onComplete {
    case Success(usr) => println(s"We have a user with id=${usr.id} named ${usr.name}")
    case Failure(ex) => //handle exception
  }

}