import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.5",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Willems Akka Typed Experiments",
    libraryDependencies += scalaTest % Test,
    libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed"     % "2.5.17",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream"          % "2.5.17",
    libraryDependencies += "com.typesafe.akka" %% "akka-http"            % "10.1.4",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream"          % "2.5.17",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j"           % "2.5.17",
    libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.4",
    libraryDependencies += "org.slf4j"         % "slf4j-api"             % "1.7.25"
  )

