# Chai Chat

A simple Android chat app for Chai AI. One screen: your messages, its replies, a
text box. Settings holds your API key.

## What it does

- Send a message, get a reply, in a normal chat thread.
- The conversation is saved on the phone and is still there when you reopen the app.
- A message that fails to send stays in the thread marked as not sent, with a
  Retry button, instead of disappearing.
- Optional system prompt, so the bot keeps a consistent persona across the chat.
- Clear conversation, with a confirmation.

That is the whole app. No accounts, no sync, no analytics, no third party SDKs.

## Before it will send anything

You need a Chai API key from your own Chai account. Open the app, tap the overflow
menu, choose Settings, paste the key, and save.

## One thing you should check first

`api.chai-research.com` is blocked by the network policy on the machine this was
written on, so I could not read Chai's API reference or make a single live call.
The endpoint and the auth header name come from Chai's published documentation as
it appeared in search results:

- `POST https://api.chai-research.com/v1/chat/completions`
- API key sent in an `X-API_KEY` header

The request body follows the OpenAI chat completions convention that the endpoint
path and the operation name (`createChatCompletion`) both point to. **That part is
an inference, not something I verified.**

Two things make a wrong inference cheap to fix rather than a rewrite:

1. All of it lives in one file, `net/ChaiApi.kt`. Field names are in one place.
2. The response parser tries several plausible shapes, and when none match it
   shows you the raw response body on screen instead of an empty bubble. So the
   first message you send tells you what the real shape is.

If it does not work first time, send me what that error card says and it is a
few lines to correct. The base URL is editable in Settings too, so a moved
endpoint does not need a new build.

## Build

There is no Android SDK in the environment this was written in, so the Android
layer has never been compiled. The pure logic is covered by tests that do run.

```bash
# Android Studio: open the android-chai-chat folder and let it sync.

# Command line, with ANDROID_HOME set and platform 34 plus build tools installed:
cd android-chai-chat
gradle wrapper --gradle-version 8.7   # first time only
./gradlew test                        # 27 unit tests
./gradlew installDebug                # onto a connected phone
```

Minimum Android 7.0, target Android 14.

## How it is put together

```
net/ChaiApi          the wire format, request and response, all in one place
net/ChaiClient       one POST over HttpURLConnection, with readable failures
data/ChatMessage     a message, plus the JSON codec for saving the thread
data/ConversationStore  the thread on disk, capped at 300 messages
data/TurnBuilder     builds the request history from the thread
data/Settings        API key, model, base URL, system prompt
ui/ChatViewModel     owns the conversation and the send logic
ui/ChatActivity      the chat screen
ui/SettingsActivity  the settings screen
```

No JSON library: `org.json` ships with Android. Networking is
`HttpURLConnection`, because one request and one response does not justify an
HTTP client dependency.

## Tests

27 unit tests over the parts where a bug is silent rather than obvious:

- **Wire format.** Turn ordering and roles, optional fields present only when set,
  quotes and backslashes and newlines escaped properly, every response shape the
  parser claims to handle, and the unrecognised case that keeps a wrong guess
  visible.
- **Saving and loading.** Round trip including emoji, Arabic and Japanese text,
  tabs and quotes. A half written file loses the history rather than crashing the
  app on every launch. A message interrupted mid-send comes back as failed and
  retryable, not stuck on "sending" forever.
- **History building.** Failed messages are never sent as though they arrived, the
  history is capped so a long chat does not grow the request until it is rejected,
  and the cap never pushes out the system prompt.

## Privacy

Your messages go to Chai and nowhere else. The API key and the conversation are
held in the app's private storage. Backup and device transfer are switched off in
the manifest, so neither is carried off the phone in a cloud backup or a phone
migration. Cleartext HTTP is disabled, so the connection cannot silently downgrade
from HTTPS.

## Known limits

- No streaming. The reply appears when it is complete. Chai's API may support
  streaming; adding it is contained to `ChaiClient` if you want it.
- One conversation, not many. Clearing it starts fresh.
- No image or voice messages.
