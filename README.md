# slakka-bot

[![travis status](https://travis-ci.org/dvmlls/slakka-bot.svg?branch=master)](https://travis-ci.org/dvmlls/slakka-bot)

## Prerequisites

### SBT

Simple Build Tool, written in scala: http://www.scala-sbt.org/0.13/docs/Setup.html

```
$ sbt 'inspect sbtVersion'
[info] Set current project to slakka-bot (in build file:/Users/dmills/src/slakka-bot/)
[info] Setting: java.lang.String = 0.13.9
```

On OSX: `brew install sbt`

### IntelliJ

IntelliJ 15 Community: https://www.jetbrains.com/idea/download/ 

When it starts up:
* Configure --> Plugins
* Install JetBrains Plugin...
* Install the `scala` plugin

### Java

The Java 8 SDK: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

```
$ java -version
java version "1.8.0_60"
Java(TM) SE Runtime Environment (build 1.8.0_60-b27)
```

To change your default java on OSX: http://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-os-x
