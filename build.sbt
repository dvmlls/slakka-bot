scalaVersion := "2.11.6"

scalacOptions ++= Seq("-deprecation", "-feature")

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %% "spray-client" % "1.3.3",
  "io.spray" %%  "spray-json" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "javax.websocket" % "javax.websocket-api" % "1.1",
  "org.glassfish.tyrus" % "tyrus-client" % "1.10",
  "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.slf4j" % "slf4j-simple" % "1.7.12"
)
