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

I added some config to tell the bot to log to `stderr` instead of `stdout`, allowing me to fiddle with it from the command line.

I'm going to use two terminals: one to interact with the bot, and the other to watch the logs.

In one terminal:
```
$ export SLACK_TOKEN="[your slack token]"
$ sbt 2>>~/bot.log
[info] Set current project to slakka-bot (in build file:/Users/dmills/src/slakka-bot/)
> console 
[info] Starting scala interpreter...
scala> Bot.main(null)
scala> 
```

In another terminal:
```
$ tail -f ~/bot.log
...
2016-01-29 18:45:25:178 [default-akka.actor.default-dispatcher-4] INFO Bot$Brain - found websocket URL, users (183), and channels (137)
2016-01-29 18:45:25:179 [default-akka.actor.default-dispatcher-3] INFO Bot$Brain - connecting
2016-01-29 18:45:25:179 [default-akka.actor.default-dispatcher-3] INFO WebSocketClient - connecting to: wss://ms510.slack-msgs.com/websocket/oa8J1-ksBX3FCEo1LtxIoVxNc8kE9kXkV2wPl3RqFKtYQhoveZS6SgXWKNYVEFj1do05JFCK1LpMT9oE_CkMWqmu61MIys29I5PL1tZ_2xkYNYqGMebv-0oKhdg03BHNn5x6A3qUoK6p-ncoAjT5zg==
2016-01-29 18:45:25:886 [default-akka.actor.default-dispatcher-3] INFO WebSocketClient - connected
2016-01-29 18:45:25:899 [default-akka.actor.default-dispatcher-3] INFO Bot$Brain - unhandled message: {"type":"hello"}
...
```

Send someone a direct message:
```
scala> Bot.brain ! Bot.UserChat("dave", "your face")
```

[Imgur](http://i.imgur.com/YhidXhl.png)

When they reply, it'll show up in the logs:
```
2016-02-01 11:12:12:030 [default-akka.actor.default-dispatcher-4] INFO Bot$Brain - unhandled message: {"type":"user_typing","channel":"D0K3XHE3Y","user":"U06DF12SU"}
2016-02-01 11:12:13:718 [default-akka.actor.default-dispatcher-4] INFO Bot$Brain - UserChat(dave,my face what?)
2016-02-01 11:12:22:974 [default-akka.actor.default-dispatcher-4] INFO Bot$Brain - unhandled message: {"type":"reconnect_url","url":"wss://ms510.slack-msgs.com/websocket/OdLoJNQiyRcXmD07VIQ8qvr_IQuIOo7Az
```


To shut down your bot cleanly, terminate the actor system, which shuts down all the background threads:
```
scala> Bot.system.terminate()
scala> :quit
[success] Total time: 208 s, completed Jan 29, 2016 11:22:29 AM
$ 
```