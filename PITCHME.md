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
- preStart()/preRestart()/postStop()/postRestart()
- context.become()
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

### Goodbye Actor, hello Behavior

+++?code=src/main/scala/Example1.scala&lang=scala&title=Our first typed actor system

@[1-2](Imports from akka.actor.typed)
@[6](Our single message type)
@[8-13](Behavior for messages of type Greet. Note we cannot send a reply here)
@[15-16](Definition of our ActorSystem, which is in itself an ActorRef)
@[18](Send a message to our single actor(system))
---
### Output from first typed actor system
```scala
$ sbt run
Received a greeting Hello
```
---

### Using the ask pattern
+++?code=src/main/scala/Example2.scala&lang=scala&title=How to use the ask pattern

@[4](Importing the ask pattern)
@[8](Importing future because an ask terminates in the future)
@[13-15](Define type Response and add it to Greet)
@[17](Signature of main actor stays the same!)
@[17-24](We now have a replyTo to send Response to)
@[29-32](Context setup)
@[34-35](Create a future which terminates when we receive an answer)
@[35](Ref is of type ActorRef[Response])
@[37-39](Do something with the result)

---
### Output from ask example
```scala
$ sbt run
Received a greeting Hello
Received answer: You say Hello I say Goodbye!
```
---
### Behavior creation, initialization and state
+++?code=src/main/scala/Example3.scala&lang=scala&title=Behavior setup and state

@[11-21](A new behavior to count the number of messages received)
@[18](Return behavior with new state)
@[29](Use Behaviors.setup to get access to context)
@[31-32](Create one child with a unique name)
@[37](Send a notification to greetCounter)
@[52-61](Send three messages to root actor)

---
### Output from behavior setup and state example
```scala
$ sbt run
Received a greeting Hello Julia
Received msg nr 0 Hello Julia
Received a greeting Hello Emma
Received a greeting Hello Sophie
Received answer: You say Hello Julia I say Goodbye!
Received answer: You say Hello Emma I say Goodbye!
Received answer: You say Hello Sophie I say Goodbye!
Received msg nr 1 Hello Emma
Received msg nr 2 Hello Sophie
```

---

### Thank you!

Slides created with GitPitch