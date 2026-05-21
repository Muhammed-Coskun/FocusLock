# FocusLock

Ein Anti-Doomscroll-Blocker für Android. Selbst auferlegte Sperren für Apps **und** Webseiten — designed in Samsung One UI 8 Look.

> **Regel:** 15 Min Nutzung → 2 h gesperrt → 15 Min frei → … Sperren lassen sich nur durch ein bewusst nerviges Puzzle vorzeitig lösen.

UI ist auf Deutsch. Getestet auf einem Samsung Galaxy S24 Ultra (Android 14+).

---

## Features

- **App-Sperre** per `UsageStatsManager`-Polling — keine Modifikation der Ziel-Apps nötig.
- **Webseiten-Sperre** in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, DuckDuckGo, Vivaldi über einen Accessibility-Service, der die URL-Leiste ausliest. Beispiel: `twitter.com` wird genauso behandelt wie eine App.
- **Geteilter Timer (Gruppen):** Mehrere Einträge (z. B. Insta-App + `instagram.com` + `twitter.com`) können sich ein gemeinsames 15/2-h-Budget teilen — damit ein Sperrwechsel zwischen Surfaces unmöglich wird. Gruppen-Mitgliedschaft ist während aktiver Sperre eingefroren.
- **Notfall-Entsperrung per Puzzle:** 60-Zeichen-Zufallsstring exakt abtippen. `FLAG_SECURE` blockt Circle to Search, Screenshots und Bildschirm-Reader; Copy/Paste-Menü ist deaktiviert; Bulk-Input wird abgewiesen.
- **Vorwarnung bei 12 von 15 min:** Toast „Noch 3 Min Instagram, dann Sperre." über jeder App, damit der Block nicht überraschend kommt.
- **Statistik-Schicht:** Streak-Counter (Tage ohne Notfall-Entsperrung), Tagessummen, 7-Tage-Balkendiagramm, pro-App-Aufschlüsselung. Historie 60 Tage, In-Memory-Batching alle 15 s.
- **Samsung One UI 8 Look:** erzwungener Dark Mode, pastellige Akzentfarben, große Display-Typo mit negativem Letter-Spacing, runde Tiles.

## Architektur

```
app/src/main/java/com/personal/focuslock/
├── Constants.kt
├── MainActivity.kt                    # Compose-Host, Navigation, Permission-Banner
├── data/
│   ├── AppState.kt                    # AppState + MonitorGroup (geteilter Timer)
│   ├── BlockerRepository.kt           # DataStore-Wrapper, atomic states+groups updates
│   ├── ForegroundTracker.kt           # UsageStatsManager-Wrapper
│   ├── WebMonitor.kt                  # In-Process Bus für Browser-Host
│   ├── StatsRepository.kt             # Per-Tag/-App Statistik, batch-flushing
│   └── StatsModels.kt
├── service/
│   ├── BlockerService.kt              # Foreground-Service, 1 s Poll-Loop
│   ├── BrowserAccessibilityService.kt # Liest URL-Leisten in bekannten Browsern
│   └── BootReceiver.kt
├── ui/
│   ├── HomeScreen.kt                  # Streak, Übersicht, Solo+Gruppen-Karten
│   ├── AppDetailScreen.kt             # Status, Gruppen-Toggles, Aktionen
│   ├── StatsScreen.kt                 # Streak, Heute, 7-Tage-Chart
│   ├── AppPickerScreen.kt             # App + Web-Domain hinzufügen
│   ├── PuzzleActivity.kt              # Notfall-Entsperrung (transkriptions-basiert)
│   ├── BlockOverlayActivity.kt        # Vollflächiger Block-Screen
│   ├── theme/                         # Samsung-Palette, Forced Dark Theme, Typo
│   ├── HomeViewModel.kt
│   └── StatsViewModel.kt
└── util/
    ├── Permissions.kt
    └── TimeFormat.kt
```

### Wie der Block tatsächlich funktioniert

1. `BlockerService` läuft als Foreground-Service und pollt jede Sekunde.
2. `ForegroundTracker` fragt `UsageStatsManager` nach `ACTIVITY_RESUMED`/`PAUSED`-Events und ermittelt das aktuelle Vordergrund-Paket.
3. Ist das Paket ein bekannter Browser, wird zusätzlich `WebMonitor.state.host` konsultiert (gefüllt vom `BrowserAccessibilityService`); ist eine überwachte Domain im Bild, gilt der synthetische Schlüssel `web:twitter.com` als „effektiver Vordergrund".
4. Pro Tick wird `usedMs` des betroffenen Containers (Einzel-`AppState` oder gemeinsame `MonitorGroup`) um `delta` erhöht. `delta` kommt aus `SystemClock.elapsedRealtime()` — immun gegen Wall-Clock-Sprünge (NTP-Sync, DST, Mitternacht); werden > 3 s übersprungen, wird angenommen das Gerät schlief und es wird gar nichts addiert.
5. Bei 15 min wird `blockUntilMs = now + 2 h` gesetzt.
6. Beim nächsten Tick mit gesperrter App im Vordergrund startet die `BlockOverlayActivity` (im SYSTEM-ALERT-WINDOW-Stil) + zusätzlich ein HOME-Intent, damit die Ziel-App in den Hintergrund geht.
7. Statistik (`StatsRepository`) puffert Sekunden in Memory und schreibt alle 15 s atomar in einen separaten DataStore.

### Wie der Puzzle-Bypass abgesichert ist

- **`FLAG_SECURE`** auf der PuzzleActivity blockt Screenshots, Bildschirmaufzeichnung, Google Lens und Circle to Search (gleiches Flag wie Banking-Apps).
- **No-Op `LocalTextToolbar`** → Long-Press im Eingabefeld zeigt kein Cut/Copy/Paste-Menü.
- **`KeyboardOptions(autoCorrect=false, capitalization=None, keyboardType=Ascii)`** → die Tastatur kann den Text nicht vorschlagen.
- **Bulk-Input-Schutz**: ein einzelnes Update das mehr als 4 Zeichen hinzufügt wird verworfen (= Paste).

## Build

### Voraussetzungen

- **JDK 17** (z. B. Eclipse Temurin oder Zulu)
- **Android SDK** mit `compileSdk 35` (Platforms → Android 15)
- Android Studio Hedgehog oder neuer, **oder** CLI mit Gradle 8.9

Eine `local.properties` mit `sdk.dir=...` wird beim ersten Öffnen in Android Studio automatisch erzeugt.

### CLI-Build

```bash
git clone https://github.com/Muhammed-Coskun/FocusLock.git
cd FocusLock
./gradlew :app:assembleDebug
# APK liegt unter app/build/outputs/apk/debug/app-debug.apk
```

Auf Windows: `gradlew.bat :app:assembleDebug`.

### Installation

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Erst­einrichtung

Beim ersten Start erscheint ein Permission-Banner. Reihenfolge:

1. **Nutzungsdatenzugriff** — Einstellungen → Spezieller Zugriff → Nutzungsdaten → FocusLock aktivieren. Benötigt um zu wissen welche App vorn ist.
2. **Über anderen Apps anzeigen** — damit der Block-Screen über z. B. Instagram laufen kann.
3. **Bedienungshilfe** *(nur für Webseiten-Sperre nötig)* — Einstellungen → Bedienungshilfe → FocusLock aktivieren. Android zeigt eine Warnung, dass die App Bildschirminhalte lesen kann — das ist erwartet. Verwendet ausschließlich um die URL-Leiste in bekannten Browsern zu lesen; der erkannte Host bleibt nur in einem In-Memory `StateFlow`.

Für Samsung-Geräte zusätzlich empfohlen: **Apps → FocusLock → Akku → Uneingeschränkt**, sonst killt OneUI den Service im Hintergrund.

## Parameter

In [`Constants.kt`](app/src/main/java/com/personal/focuslock/Constants.kt):

```kotlin
const val USAGE_LIMIT_MS: Long = 15L * 60 * 1000          // erlaubte Nutzung
const val BLOCK_DURATION_MS: Long = 2L * 60 * 60 * 1000   // Sperrdauer
const val WARN_BEFORE_BLOCK_MS: Long = 3L * 60 * 1000     // Vorwarnung
const val POLL_INTERVAL_MS: Long = 1000L                   // Poll-Tick
const val STATS_HISTORY_DAYS = 60                          // Stats-Aufbewahrung
```

## Limitationen

- Polling-Latenz ~1 s. Direkt nach App-Öffnen siehst du kurz die App, bevor der Block-Screen kommt.
- Browser ohne in [`WebMonitor.KNOWN_BROWSERS`](app/src/main/java/com/personal/focuslock/data/WebMonitor.kt) gelistetes Paket werden nicht erkannt — Liste ist erweiterbar.
- Die Web-Sperre erkennt URLs anhand der URL-Leiste, nicht durch Netzwerk-Inspektion. Browser ohne sichtbare URL-Leiste (sehr selten) werden nicht erfasst.
- Ein VPN-basierter Layer (echte Host/IP-Filterung statt Accessibility) ist möglich, aber bewusst nicht implementiert — wäre 1000+ Zeilen Paket-Parsing für ein im Vergleich marginales Plus.

## Privatsphäre

- Keine Netzwerk-Calls. Keine Telemetrie. Keine Cloud-Sync.
- Der Accessibility-Service liest nur die URL-Leiste in der Liste der bekannten Browser, behält ausschließlich den Host (`twitter.com`) im RAM und schreibt **nichts** auf Disk.
- App-Nutzungsdaten werden lokal in `androidx.datastore` gehalten und nie verlassen das Gerät.

## Lizenz

MIT — siehe [LICENSE](LICENSE).
