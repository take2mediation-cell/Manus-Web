# Chai Narrator

A floating bubble that sits on top of the Chai app and reads its replies aloud in a
voice you choose. Android only. Everything runs on the handset and the app has no
internet access at all.

## What this is, and what it is not

You asked for a persistent voice narrator bubble that reads whatever Chai puts on
screen. On Android that is a real, buildable thing, and this is it. Three points
are worth stating plainly before you start.

**It is a native Android app, not a web page.** Reading another app's on-screen
text requires an AccessibilityService, and drawing a bubble over other apps
requires the overlay permission. Neither is available to a website or a PWA, so
this could not be built into the web project in this repository.

**It cannot be built for iOS.** iOS has no cross-app text capture and no floating
overlays outside of Picture in Picture. There is no workaround, private API
tricks included. If your phone is an iPhone, the closest available options are
Siri's Speak Screen gesture or VoiceOver, both built into iOS.

**Nothing here can promise "a forty year old woman".** Android's text-to-speech API
exposes a voice's locale, quality and latency. It does not expose age, and its
gender labelling is a naming convention in Google's engine rather than part of the
API. What the app does instead is rank the voices most likely to read as calm,
female and mid-range, put a Test button beside every one, and default the pace and
pitch slightly below the engine's defaults, which is what actually makes a voice
sound settled and adult rather than bright and young. You pick the one that sounds
right. Pace and pitch move the perceived age more than the voice choice does, so
adjust those first if the voice is close but not quite there.

## Behaviour

| You asked for | How it works |
| --- | --- |
| Always visible floating bubble | A foreground service holds a draggable overlay that snaps to the nearest edge and survives app switching. |
| Read any new text automatically | The accessibility service watches the chosen app, waits for the screen to hold still for 900 ms, then reads whatever is genuinely new. |
| Summarise long messages only when necessary | Off until a message passes a threshold you set, default 700 characters. Below that everything is read in full. |
| Never interrupt unless there is new text | The service speaks only on new content. Repeats, re-scrolls and the backlog you see when you open the app are all silent. |
| Read instructions verbatim | Numbered steps and imperative lists are detected and never summarised while that option is on. |
| Clear enunciation, natural pacing | Text is stripped of markdown, emoji and raw URLs, then read sentence by sentence with a real pause between them, longer after a full stop than after a comma. |

Tap the bubble once to silence it instantly and again to resume. Long press to open
settings. Drag it anywhere.

## Build

There is no Android SDK in the environment this was written in, so the project has
never been compiled end to end. The pure text logic is covered by unit tests that
do run, and those pass. Treat the Android layer as reviewed but not yet built.

```bash
# Android Studio: open the android-chai-narrator folder and let it sync.

# Command line, with ANDROID_HOME set and platform 34 plus build tools installed:
cd android-chai-narrator
gradle wrapper --gradle-version 8.7   # first time only
./gradlew test                        # the text pipeline tests, 21 of them
./gradlew assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug                # straight onto a connected handset
```

Minimum Android 8.0, target Android 14.

## Setting it up on the phone

The app opens on a four step checklist. Each step turns from a circle to a tick
once it is done.

1. **Allow the floating bubble.** Opens the "display over other apps" screen.
2. **Turn on the narrator service.** Opens Accessibility settings. Find Chai
   Narrator in the list and switch it on. Android will show a warning about full
   screen access; that warning is accurate and applies to every screen reader.
3. **Pick the app to read.** The narrator listens to this one app and ignores
   everything else. Chai is floated to the top of the list. The package name is
   not hard coded, because it changes between builds and a wrong guess would leave
   the narrator silent with nothing to explain why.
4. **Pick the voice.** Audition with Test, tap the row to keep it.

Then press Start bubble.

If the list of voices is short or nothing speaks, install Google's speech services
and download an offline English voice in Settings, Accessibility, Text-to-speech
output. Voices marked as needing network cannot work here, because the app has no
internet permission.

## Privacy

See [PRIVACY.md](PRIVACY.md). The short version: the app has no INTERNET
permission, so the platform blocks every network socket it could open. On-screen
text is read into memory, spoken, and discarded. Nothing is written to disk except
your own settings, and backup is switched off. Text you are typing is never read,
because editable fields are skipped before anything else happens.

## How it is put together

```
service/ChaiAccessibilityService   watches the chosen app, decides when to read
service/BubbleService              the overlay and the foreground notification
text/TextExtractor                 pulls leaf text from the view tree, skips input fields
text/SpokenHistory                 works out what is genuinely new
text/SpeechShaper                  makes raw UI text readable aloud
text/Summarizer                    shortens long passages without inventing anything
tts/NarratorEngine                 one shared TextToSpeech instance
tts/VoiceCatalog                   ranks voices, best candidates first
ui/                                the setup checklist and the two pickers
```

The three classes in `text/` carry the logic worth testing and have no Android
dependencies, so `./gradlew test` covers them on the desktop JVM. Three of those
tests are regression guards for bugs found while writing this: emoji that were
never stripped, numbered steps that were never detected, and a summary budget that
could quietly ignore your threshold setting.

## Known limits

- The 900 ms settle delay is a compromise. Shorter and a reply that types itself
  out gets read in fragments; longer and the narrator feels slow. It is a constant
  in `ChaiAccessibilityService` if you want to tune it.
- Text rendered as an image cannot be read. Nothing on the device can read that
  without OCR, which would mean shipping a model and is out of scope here.
- Aggressive battery optimisation on some handsets kills foreground services.
  If the bubble disappears after a while, exempt the app from battery optimisation
  in its App info screen.
- The summariser is extractive word statistics, not a model. It can only repeat
  sentences Chai actually displayed, which is the point: it cannot put a line in
  the narrator's mouth that was never on screen. It is not as smooth as a model
  would be.
