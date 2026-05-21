# FocusLock

Selbst auferlegter App- und Webseiten-Blocker für Android, designed für Samsung One UI 8.

## Idee

Schluss mit Doomscrolling, ohne sich die App komplett zu verbieten. FocusLock folgt einem festen Rhythmus:

> **15 Minuten Nutzung → 2 Stunden gesperrt → 15 Minuten frei → …**

Jede überwachte App und jede überwachte Webseite läuft nach diesem Muster. Erreicht der Zähler 15 min, blendet sich ein Vollbild-Sperrscreen über die Ziel-App und zwingt dich zurück auf den Homescreen. Erst nach Ablauf der 2 h öffnet sich der Zugriff für die nächsten 15 min — oder du löst die Notfall-Entsperrung.

## Was es kann

- **Apps** sperren (Instagram, TikTok, …) via `UsageStatsManager`.
- **Webseiten** im Browser sperren (Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, DuckDuckGo, Vivaldi). Damit lässt sich der Trick „Insta gesperrt → schnell `twitter.com` in Chrome" nicht mehr nutzen.
- **Geteilter Timer:** Mehrere Einträge (z. B. Insta-App + `instagram.com` + `twitter.com`) lassen sich zu einer Gruppe verbinden, die sich ein gemeinsames 15/2-h-Budget teilt. Während einer aktiven Sperre ist die Mitgliedschaft eingefroren.
- **Notfall-Entsperrung:** ein bewusst nerviger 60-Zeichen-Transkriptions-Puzzle. `FLAG_SECURE`, kein Paste, keine Tastatur-Vorschläge — damit Circle to Search, Lens & Friends nicht reinpfuschen.
- **Vorwarnung** als Toast bei Minute 12 von 15.
- **Statistik:** Streak (Tage ohne Notfall-Entsperrung), 7-Tage-Chart, Tageswerte pro App, 60 Tage Historie.

## Wie das technisch zusammenhängt

- Ein **Foreground Service** pollt jede Sekunde den Vordergrund-Prozess.
- In bekannten Browsern liest ein **Accessibility Service** die URL-Leiste aus und meldet den Host an einen In-Memory-Bus — keine URL verlässt jemals das Gerät.
- Pro Tick wird `usedMs` des passenden Containers (Solo-`AppState` oder gemeinsame `MonitorGroup`) erhöht. Bei 15 min → `blockUntilMs = jetzt + 2 h`. Ab dann fängt jeder erneute Aufruf den `BlockOverlayActivity` ab.
- Sperr-Zähler nutzt `SystemClock.elapsedRealtime()` — immun gegen Wall-Clock-Sprünge (Mitternacht, NTP, DST).
- Persistenz in `androidx.datastore` als JSON, keine Cloud, kein Logging, keine Network-Calls.

Mehr Details direkt im Code unter [`app/src/main/java/com/personal/focuslock/`](app/src/main/java/com/personal/focuslock/).

## Bauen

```bash
./gradlew :app:assembleDebug
```

Voraussetzungen: JDK 17, Android SDK mit `compileSdk 35`. APK landet unter `app/build/outputs/apk/debug/`.

## Lizenz

MIT — siehe [LICENSE](LICENSE).
