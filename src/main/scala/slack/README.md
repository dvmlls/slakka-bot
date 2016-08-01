# Creating a Slack Chat Bot

## Get a Token

Get your own slack token, or create a bot user: https://my.slack.com/services/new/bot 

## Get the URL for the websocket client

POST the following Form Data here ([docs](https://api.slack.com/methods/rtm.start)): https://slack.com/api/rtm.start 
* "token" -> [the token you got above]

This returns a JSON snippet like:

```json
{
  "ok": true,
  "url": "wss:\/\/ms9.slack-msgs.com\/websocket\/7I5yBpcvk",
  "self": {
      "id": "U023BECGF",
      "name": "bobby"
  }
}
```

## Open a WebSocket connection to that URL to Listen

This is the Slack Real-Time API ([docs](https://api.slack.com/rtm)).

Among others, you'll get messages ([docs](https://api.slack.com/events/message)) that look like this:
```json
{
    "type": "message",
    "channel": "C2147483705",
    "user": "U2147483697",
    "text": "Hello world",
    "ts": "1355517523.000005"
}
```

## Chat Back

Send messages over that websocket connection:
```json
{
    "type": "message",
    "channel": "C2147483705",
    "text": "Hello world",
}
```
