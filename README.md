# FocusLock

Android app/website blocker with a fixed usage cycle.

## How it works

> **15 minutes of use → 2 hours locked → 15 minutes → …**

Every monitored app and every monitored website follows the same cycle. When the counter hits 15 min, a full-screen lock activity opens over the target and you get bounced to the home screen until the 2 h are up. Solving the emergency puzzle resets the cycle early.

## Features

- App blocking via `UsageStatsManager`.
- Website blocking in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, DuckDuckGo and Vivaldi — an `AccessibilityService` reads the URL bar so the same cycle applies to e.g. `twitter.com`.
- Shared-timer groups: multiple entries (Insta app + `instagram.com` + `twitter.com`) can share one 15/2 h budget. Group membership is frozen while a block is active.
- Emergency unlock: 60-character random-string transcription puzzle. `FLAG_SECURE` blocks Circle to Search and screenshots, paste menu disabled, keyboard suggestions off, bulk-input rejected.
- 12-of-15 min warning toast.
- Stats: streak counter, daily totals, 7-day chart, per-app breakdown, 60 days of history.

## Implementation notes

- Foreground service polls every second, ticks `usedMs` on the matching `AppState` or shared `MonitorGroup`.
- Browser host detection runs in an in-memory `StateFlow` — nothing about your browsing is persisted or sent anywhere.
- `SystemClock.elapsedRealtime()` for delta math, immune to wall-clock jumps (NTP, DST, midnight rollover).
- Service self-restarts via `onTaskRemoved` + a 10-minute inexact AlarmManager watchdog to survive Doze and Samsung's background-kill behavior.
- Persistence via `androidx.datastore` (JSON). No network calls, no telemetry.

Source under [`app/src/main/java/com/personal/focuslock/`](app/src/main/java/com/personal/focuslock/).

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17 and Android SDK with `compileSdk 35`.

## License

MIT — see [LICENSE](LICENSE).
