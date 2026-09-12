# Chai Voice Agent (Android)

A small Android app that reads new chat text out loud while you're inside the
**Chai AI** app, using Android's built-in Accessibility API to see what's on
screen and Android's built-in Text-to-Speech engine to speak it. Nothing is
sent off the device: no network calls, no analytics, no accounts.

## How it works

- `ChaiScreenReaderService` is an `AccessibilityService` scoped to Chai's
  package(s) only (`com.Beauchamp.Messenger.external` — "Chai: Chat AI
  Platform" — and `com.freechat.android.chaix` — "Chai.X" — by default). It
  never receives events from any other app.
- On every screen change inside Chai, it walks the visible accessibility node
  tree, picks out text that hasn't been spoken yet (skipping the text-entry
  box so it doesn't read back what you're typing), and queues it on Android's
  on-device `TextToSpeech` engine.
- A small notification lets you pause/resume speaking without turning off the
  accessibility permission. The in-app switch does the same thing.

## Project layout

```
android-voice-agent/
  app/src/main/java/com/take2mediation/chaivoiceagent/
    MainActivity.kt              settings screen (enable/disable, rate, pitch, target packages, test voice)
    ChaiScreenReaderService.kt   the AccessibilityService that reads the screen and speaks
    SpeechEngine.kt              thin wrapper around android.speech.tts.TextToSpeech
    NotificationActionReceiver.kt handles the Pause/Resume notification action
    Prefs.kt                     SharedPreferences-backed settings
  app/src/main/res/xml/accessibility_service_config.xml   default target packages + event types
```

## Build & run

1. Open the `android-voice-agent/` folder as a project in Android Studio
   (Koala or newer). It will offer to add the Gradle wrapper automatically if
   one isn't present — accept that, or run `gradle wrapper --gradle-version 8.7`
   yourself if you have Gradle installed.
2. Let Gradle sync, then Run on a device or emulator with the Chai app
   installed (minSdk 26 / Android 8.0+).
3. In the app, tap **Open Accessibility Settings**, find "Chai Voice Agent
   Screen Reader" under Downloaded/Installed apps, and turn it on. Android
   will show a permission dialog explaining this lets the service read
   screen content — that's expected, and by the config above it only fires
   for Chai's package(s).
4. Open Chai and start chatting — new messages should be read aloud.

## Settings screen

- **Speak new messages** — master on/off switch (also toggled from the
  status notification's Pause/Resume action).
- **Speech rate / pitch** — sliders, 0.3x–2.0x.
- **Target app package(s)** — comma-separated list, defaults to Chai's known
  package IDs. Edit this if Chai ships a new build under a different package
  name, or if you want to point the same service at another chat app.
- **Test voice** — speaks a sample line without needing the accessibility
  permission, useful for checking your device's TTS engine/voice/language is
  set up the way you want (Android Settings > System > Languages & input >
  Text-to-speech output).

## Privacy notes

- The accessibility service is restricted, at the OS level, to the package
  names listed above — Android will not deliver it events from any other
  app.
- All speech synthesis happens on-device via Android's `TextToSpeech` API;
  no text or audio is uploaded anywhere.
- The app requests `POST_NOTIFICATIONS` (Android 13+) only to show the
  pause/resume status notification, and the accessibility permission itself
  (granted manually in system settings, not requested at runtime).

## Known limitations

- Chai doesn't expose an official API for "new message" events, so this uses
  a heuristic (diff against previously-seen text on screen) rather than a
  guaranteed message boundary. UI chrome text (button labels, etc.) may
  occasionally get read once the first time it appears; timestamp-like
  strings are filtered out.
- If Chai restructures its UI significantly, the heuristic may need
  retuning (see `collectNewText` / `isNoise` in `ChaiScreenReaderService.kt`).
