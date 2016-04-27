# slakka-bot

[![travis status](https://travis-ci.org/dvmlls/slakka-bot.svg?branch=master)](https://travis-ci.org/dvmlls/slakka-bot)
[![maven central](https://img.shields.io/maven-central/v/cat.dvmlls/slakka-bot_2.11.svg?maxAge=2592000)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22slakka-bot_2.11%22)

Slack chat bot built with akka.  

## Dependencies

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

I added some config to tell the bot to log to `stderr` instead of `stdout`, allowing me to fiddle with it from the command line.

I'm going to use two terminals: one to interact with the bot, and the other to watch the logs.

In one terminal:
```
slakka-bot $ export SLACK_TOKEN="[your slack token]"
slakka-bot $ sbt console 2>>~/bot.log
[info] Set current project to slakka-bot (in build file:/Users/dmills/src/slakka-bot/)
[info] Starting scala interpreter...
[info]
Welcome to Scala version 2.11.7 (Java HotSpot(TM) 64-Bit Server VM, Java 1.8.0_60).
Type in expressions to have them evaluated.
Type :help for more information.

scala>
```

Now load a sample bot:
```
scala> :load EchoBot.scala
Loading EchoBot.scala...
import java.net.URI
import java.util.concurrent.TimeUnit
import akka.actor._
...
scala> 
```

In another terminal:
```
$ tail -f ~/bot.log
...
2016-02-10 10:23:21:699 [default-akka.actor.default-dispatcher-3] INFO akka.event.slf4j.Slf4jLogger - Slf4jLogger started
2016-02-10 10:23:21:710 [default-akka.actor.default-dispatcher-3] DEBUG akka.event.EventStream - logger log1-Slf4jLogger started
2016-02-10 10:23:21:711 [default-akka.actor.default-dispatcher-3] DEBUG akka.actor.LocalActorRefProvider$SystemGuardian - now supervising Actor[akka://default/system/UnhandledMessageForwarder#654532009]
2016-02-10 10:23:21:711 [default-akka.actor.default-dispatcher-3] DEBUG akka.event.EventStream - Default Loggers started
...
```

Send someone a direct message:
```
scala> kernel ! SendIM("dave", "your face")
```

![Imgur](http://i.imgur.com/kqnDNz6.png)

When they reply, it'll show up in the logs:
```
unhandled message from Actor[akka://default/user/kernel/slack#828626119]: MessageReceived(D0K3XHE3Y,U06DF12SU,no YOUR face)
```


To shut down your bot cleanly, terminate the actor system first, shutting down all the background threads:
```
scala> system.shutdown(); sys.exit()
[success] Total time: 208 s, completed Jan 29, 2016 11:22:29 AM
$ 
```

## Releasing a new version

Publish to Sonatype staging: https://github.com/xerial/sbt-sonatype#command-line-usage

```
$ sbt publishSigned && sbt sonatypeRelease
```