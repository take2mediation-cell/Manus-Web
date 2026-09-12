# Privacy and data handling

An accessibility service can read everything on screen in the app it watches. That
deserves a straight account of what this one does with that access, because
"trust me" is not an answer.

## What the app can see

On-screen text in the single app you select, and nothing else. The service is
narrowed at runtime to that one package, so the system does not deliver it events
from any other app on the phone.

## What it deliberately does not see

Anything you are typing. Editable fields, password fields and search boxes are
skipped before any text is collected, so a half written message stays private even
though the service technically has the access to read it.

## Where the text goes

Into memory, into the speech engine, and nowhere else. It is not written to disk,
not logged, and not sent anywhere.

## Why it cannot be sent anywhere

The app does not declare `android.permission.INTERNET`. Without it, Android blocks
every network socket the process could open. This is not a policy the app chooses
to follow; it is enforced by the platform. You can verify it yourself in
`app/src/main/AndroidManifest.xml`, which is five lines of permissions you can read
in under a minute.

The practical cost is that network-only voices cannot be used. The voice picker
labels them so you are not left wondering.

## What is stored

Your settings only: which app to narrate, which voice, pace, pitch, the summary
threshold and the bubble's position. They live in one private SharedPreferences
file. Cloud backup and device transfer are both switched off in the manifest, so
they do not leave the handset even through a phone migration.

## Permissions and why each one exists

| Permission | Why |
| --- | --- |
| `BIND_ACCESSIBILITY_SERVICE` | The only way Android allows one app to read another's on-screen text. |
| `SYSTEM_ALERT_WINDOW` | Draws the bubble over other apps. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the bubble and the speech queue alive while you are in another app. |
| `POST_NOTIFICATIONS` | Android requires a visible notification for a foreground service. |
| `VIBRATE` | A short tick when you mute or unmute from the bubble. |
| `QUERY_ALL_PACKAGES` | Populates the "which app should I narrate" list. Used on that one screen and reported nowhere. |

## Retention and removal

Nothing accumulates. Spoken text is discarded as it is spoken, and the "already
read" history is 60 short fingerprints held in memory that are gone when the
service stops. Uninstalling removes the settings file with the app.

## If you are deploying this beyond your own phone

The above describes the app as written. Anyone planning to distribute it should
note that Google Play treats accessibility services and `QUERY_ALL_PACKAGES` as
sensitive and requires a declared, reviewed justification for both. Sideloading
onto your own device does not involve that review.
