# Jazz Standards Practice Tracker

A personal practice tool for jazz musicians. Track your familiarity with standards, work through a guided learning queue, and maintain your repertoire with a spaced-repetition review schedule.

## Features

- **Library** — 272 jazz standards with composer, year, and style metadata
- **Onboarding ranker** — rate your familiarity with each standard (0–3) when you first launch
- **Learning queue** — pick one song at a time to actively work on
- **Spaced repetition** — songs you know enter a review schedule (1 · 3 · 7 · 21 · 60 · 180 days)
- **Activity log** — full history of reviews, level changes, and practice sessions
- **Progress tab** — stats and streak tracking
- **Backup/restore** — export and import your data as JSON

## Familiarity levels

| Level | Meaning |
|-------|---------|
| 0 | Complete unfamiliarity — never played it |
| 1 | Could fake my way through in a pinch |
| 2 | Comfortable enough to play at a jam session |
| 3 | Second nature — fully performance-ready |

Songs at level 2 or above enter the spaced-repetition review queue automatically.

## Running the web app

Requires [Node.js](https://nodejs.org).

```sh
cd web
node server.js
```

Then open [http://localhost:3000](http://localhost:3000).

A `Start Practice Tracker.command` file is also included for double-click launch on macOS.

## Project structure

```
web/                  # Standalone HTML/JS web app
  jazz_practice.html  # Main app (single-file)
  jazz_data.js        # Song library and priority order
  server.js           # Simple local dev server

kmp/                  # Kotlin Multiplatform scaffold (Android/iOS/Web/Desktop)
```

## Customizing the song list

Edit `web/jazz_data.js`. Each entry follows the format:

```js
["Song Title", "Style", "Composer Name", year],
```

Style options: `Bop`, `Blues`, `Bossa`, `Modal`, `Trad`, or `""` for standard.

Songs added or removed here only affect new users (or after a data reset) — existing localStorage data is not modified.
