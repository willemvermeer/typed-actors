# Akka Typed Actors

## Should I Start Using Them? 

Willem Vermeer

Cleverbase November 2018

---

### Which problem are we solving?

```scala
actor ! AnyMessage
actor ! "message"
actor ! 42

actor.ask(command).mapTo[Result]
```
@[1-3](No way to know if actor can handle this type of message)
@[5](No way to know whether this cast is safe)

---

### Typed messages - tell

```scala
case class Greet(msg: String)

val actor: ActorRef[Greet] = ???

actor ! Greet("Hello")

actor ! "message" // won't compile
```
---

### Typed messages - ask

```scala
case class Greet(msg: String, replyTo: ActorRef[Response])
case class Response(resp: String)

val actor: ActorRef[Greet] = ???

val result: Future[Response] = 
  actor ? ref => Greet("Hello", ref)
```

---

### What will we gain?

type safety!

---
### What will we lose?

@ul

- inheritance from Actor
- sender()
- forward()
- preStart
- flexibility?

@ulend

---

### Dependency

```scala
libraryDependencies += 
  "com.typesafe.akka" %% "akka-actor-typed" % "2.5.17"
```

This includes the untyped actor code!

Actually typed.ActorSystem is built on the untyped ActorSystem.

---

### Imports

```scala
import akka.actor.ActorSystem
import akka.actor.typed.ActorSystem
import akka.actor.ActorRef
import akka.actor.typed.ActorRef

```

Watch out which one you refer to!

---
### Sample code presenting

```scala
sealed trait MyTrait
case class Willem(str: String) extends MyTrait
// hier staat commentaar
object Companion {}

```

@[1](Regel 1)
@[3-4](Dit is code met commentaar)


---
### Nu proberen te refereren aan een locale file

+++?code=src/main/scala/com/example/cb/Api.scala&lang=scala
@title[Sample Source File]

@[1-3](Da begin)
@[33-38](Hier gebeurt het)

---
### Nu proberen te refereren aan een locale file

+++?code=build.sbt&lang=scala
@title[Sample Source File]

@[1-3](Da begin)
@[10-15](Hier gebeurt het)
