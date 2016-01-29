# slakka-bot

[![travis status](https://travis-ci.org/dvmlls/slakka-bot.svg?branch=master)](https://travis-ci.org/dvmlls/slakka-bot)

Slack chat bot built with akka.  

## Prerequisites

### Java

The virtual machine the bot runs on, and the SDK necessary to target it. 

The Java 8 SDK: http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html

```
$ java -version
java version "1.8.0_60"
Java(TM) SE Runtime Environment (build 1.8.0_60-b27)
```

To change your default java on OSX: http://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-os-x

### SBT

Simple Build Tool, an interactive compilation environment: http://www.scala-sbt.org/0.13/docs/Setup.html

On OSX:
```
$ brew install sbt
```

I'm using version 0.13.9: 
```
$ sbt 'inspect sbtVersion'
[info] Set current project to slakka-bot (in build file:/Users/dmills/src/slakka-bot/)
[info] Setting: java.lang.String = 0.13.9
```

### IntelliJ

IntelliJ 15 Community: https://www.jetbrains.com/idea/download/ 

When it starts up, install the `scala` plugin:
* Configure --> Plugins
* Install JetBrains Plugin...
* Install the `scala` plugin

## Get a Slack Token

Go to https://my.slack.com/services/new/bot to register your bot. If you have multiple slack accounts, you can use https://[TEAM].slack.com/services/new/bot instead. 

Call it whatever you want, and save your token somewhere - you'll pass it on every API call you make. 

You can review your tokens here: https://api.slack.com/web#authentication 

## Run a Basic Bot

The one bit of config I added tells the bot to log to `stderr` instead of `stdout` - this allows me to interact with it from the command line.

I'm going to use two terminals: one to interact with the bot, and the other to watch the logs.

In one terminal:
```
$ sbt console 2>>~/bot.log
scala> Listener.main(Array("YOUR SLACK TOKEN"))
scala> 
```

In another terminal:
```
$ tail -f ~/bot.log
[default-akka.actor.default-dispatcher-3] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started
[default-akka.actor.default-dispatcher-2] INFO cat.dvmlls.WebSocketClient - disconnected
[default-akka.actor.default-dispatcher-3] INFO Listener$Master - disconnected
[default-akka.actor.default-dispatcher-6] INFO Listener$Master - found websocket URL, users, and channels
[default-akka.actor.default-dispatcher-3] INFO Listener$Master - connecting
```

To shut down your bot, terminate the actor system to shut down all the background threads:
```
scala> Listener.system.terminate()
scala> :quit
[success] Total time: 208 s, completed Jan 29, 2016 11:22:29 AM
$ 
```