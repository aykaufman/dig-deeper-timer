# DIG DEEPER Timer

A follow-along timer and logbook for Shaun T's 12-week DIG DEEPER dumbbell program.

## What's here

| Path | What it is |
|---|---|
| `index.html` | The phone app (served by GitHub Pages). Add it to your home screen to use it like an app. |
| `img/` | The START/FINISH photo for every move, the original files at full resolution. |
| `icons/`, `manifest.webmanifest`, `sw.js` | App icon, install info, and offline support (photos are kept on the phone after they first load). |
| `claude-artifact/dig-deeper-timer.html` | Exact copy of the timer as published on Claude, which saves to your Claude account and has the Coach card. |
| `prompts/` | The image prompts and scripts used to generate the move photos. |

## Where workouts are saved

- **Claude timer:** saved to your Claude account and backed up on that device.
- **Phone app (this repo):** saved on the phone only, in the browser's storage. Use **Download backup (CSV)** on the home screen regularly. Clearing Safari/Chrome site data erases it.

The two keep separate logs.

## Android app

Every push to `main` builds a real Android app (`.apk`) with GitHub Actions and publishes it under **Releases**.
It runs on Android's built-in WebView, so it works even with Chrome disabled. The app is small: on first launch it downloads the photos once
and keeps them on the phone, so it works offline after that and updates stay around a few MB. A banner on the home screen offers new versions.

Latest download: https://github.com/aykaufman/dig-deeper-timer/releases/latest/download/dig-deeper-timer.apk

Install: open the link on the phone, tap the downloaded file, and allow installing from that app when Android asks.
Updates install over the old version and keep your logged workouts.

The `native/` folder holds the Android-only pieces: phone voice (Android text-to-speech), backup via the share sheet,
the back button, keeping the screen on, and the app icon. The signing key lives in two repository secrets,
`ANDROID_KEYSTORE_B64` and `ANDROID_KEYSTORE_PASSWORD`, so every update is signed the same way and installs over the last one.

## Galaxy Watch app

`wear/` is a small Wear OS app that works as a remote for the phone app: it shows the current move or rest with a countdown,
− / + for weight and reps, Done, Skip rest, +15 s and Pause, and buzzes at each new step. The phone runs the workout; the
watch app talks to it over the normal watch connection (same app ID and signing key as the phone app).

Download: https://github.com/aykaufman/dig-deeper-timer/releases/latest/download/dig-deeper-watch.apk
Install it with Wear Installer 2 after turning on ADB debugging and Wireless debugging in the watch's Developer options.
