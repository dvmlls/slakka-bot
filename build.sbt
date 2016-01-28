scalaVersion := "2.11.7"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.scalacheck" %% "scalacheck" % "1.12.2" % "test",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %% "spray-client" % "1.3.3",
  "io.spray" %% "spray-json" % "1.3.2",
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "javax.websocket" % "javax.websocket-api" % "1.1",
  "org.glassfish.tyrus" % "tyrus-client" % "1.12",
  "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.12",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.1"
)

// to avoid dependency conflicts
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.4"
)