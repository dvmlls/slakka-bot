val scalaVer = "2.11.8"
scalaVersion in ThisBuild := scalaVer

lazy val commonSettings = Seq(
  organization := "cat.dvmlls",
  version := "3.10.0",
  scalaVersion := scalaVer,
  sourcesInBase := false,
  scalacOptions ++= Seq("-deprecation", "-feature", "-target:jvm-1.8")
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value) { Some("snapshots" at nexus + "content/repositories/snapshots") }
    else { Some("releases" at nexus + "service/local/staging/deploy/maven2") }
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
  homepage := Some(url("http://github.com/dvmlls/slakka-bot")),
  pomExtra :=
    <scm>
      <url>git@github.com:dvmlls/slakka-bot.git</url>
      <connection>scm:git:git@github.com:dvmlls/slakka-bot.git</connection>
    </scm>
    <developers>
      <developer>
        <id>dvmlls</id>
        <name>Dave Mills</name>
        <url>http://dvmlls.cat</url>
      </developer>
    </developers>
)

lazy val dependencies = Seq(
  "io.spray" %% "spray-can" % "1.3.4",
  "io.spray" %% "spray-client" % "1.3.4",
  "io.spray" %% "spray-json" % "1.3.2",
  "com.typesafe.akka" %% "akka-actor" % "2.4.11",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.11",
  "javax.websocket" % "javax.websocket-api" % "1.1",
  "org.glassfish.tyrus" % "tyrus-client" % "1.12",
  "org.glassfish.tyrus" % "tyrus-container-grizzly-client" % "1.12",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "org.scalatest" %% "scalatest" % "2.2.1" % "test",
  "junit" % "junit" % "4.12" % "test"
)

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(publishSettings: _*).
  settings(
    name := "slakka-bot",
    libraryDependencies ++= dependencies
  )