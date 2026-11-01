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
