# Instagram Profile Analyzer

Android-App (Kotlin, Jetpack Compose, MVVM, ab Android 8.0 / API 26) zur Analyse öffentlicher
Instagram-Profildaten und eigener Instagram-Datenexporte. Oberfläche auf Deutsch, dunkles Design.

## Funktionen

| Bereich | Umsetzung |
|---|---|
| Profilsuche | Offizielle Instagram Graph API (Business Discovery). Liefert Name, Bio, Website, Profilbild, Follower-/Gefolgt-/Beitragsanzahl und die letzten 25 Beiträge – **nur für öffentliche Business- und Creator-Konten** und nur mit eigenem Zugriffstoken. |
| Profilstatus | „Öffentlich · Business/Creator“ bei Erfolg; sonst klare Meldung „nicht abrufbar (existiert nicht, privat oder persönliches Konto)“. Eigener Kontostatus (privat/öffentlich) aus dem Datenexport. |
| Follower / Following | Import des offiziellen Datenexports (ZIP oder einzelne JSON-Dateien). Suche, Sortierung (Name, Datum), Filter (gegenseitig, folgen nicht zurück, nur mit Datum). |
| Listenvergleich | Zwei gespeicherte Datenstände (Neu / Entfernt / Unverändert) oder Follower ↔ Gefolgt innerhalb eines Stands. |
| Beiträge | Aus API-Abruf (inkl. Likes/Kommentare, soweit die API sie liefert) oder aus dem Export (Datum, Typ, Bildunterschrift; Exporte enthalten keine Like-Zahlen). Monatsdiagramm, Beiträge pro Woche. |
| Verlauf | Jeder Import wird lokal mit Datum/Uhrzeit gespeichert, einzeln oder komplett löschbar. |
| Datenschutz | Alles lokal, kein Tracking, Backup deaktiviert, „Alle Daten löschen“. |

### Bewusste Grenzen
- Keine Umgehung privater Profile, kein Scraping, keine inoffiziellen Endpunkte.
- Follower-**Listen** fremder Konten stellt Instagram über keine offizielle Schnittstelle bereit – die App zeigt dafür nur die Anzahlen (API) bzw. die eigenen Listen aus dem Datenexport.
- Keine Platzhalter- oder Beispieldaten in der App.

## Projektstruktur

```
app/src/main/java/com/ipa/analyzer/
├── core/            reine Kotlin-Logik (ohne Android, JVM-testbar)
│   ├── json/        JSON-Parser/-Writer
│   ├── model/       Datenmodelle, Speicherformat
│   ├── parser/      Instagram-Export-Parser (aktuelles + altes Layout, Umlaut-Reparatur)
│   ├── diff/        Listenvergleich, Suche/Sortierung, Beitragsstatistik
│   └── api/         Graph-API-URL-Aufbau und Antwort-Auswertung
├── data/            ZIP-Import, lokaler Speicher, HTTP-Client, Einstellungen
├── ui/              ViewModels (MVVM), Theme, Komponenten, 9 Screens
└── MainActivity.kt  Navigation (Bottom-Bar)
app/src/test/        16 JUnit-Tests für Parser, Vergleich, Speicher, ZIP, API
```

## APK bauen

### Variante A – GitHub Actions (ohne lokale Installation)
1. Neues (gern privates) GitHub-Repository anlegen und diesen Ordner hochladen.
2. Der Workflow `.github/workflows/build-apk.yml` startet automatisch: Tests → Debug-APK → Signaturprüfung → Release-APK.
3. Im Reiter **Actions** → letzter Lauf → Artefakt **InstagramProfileAnalyzer-apk** herunterladen (ZIP mit `app-debug.apk`).

### Variante B – Android Studio
1. Android Studio (Ladybug oder neuer) installieren, Ordner öffnen, Gradle-Sync abwarten.
2. *Build → Build App Bundle(s) / APK(s) → Build APK(s)*.
3. Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`.

### Variante C – Kommandozeile
Voraussetzungen: JDK 17, Android SDK (Plattform 35), Umgebungsvariable `ANDROID_HOME`.
```bash
./gradlew testDebugUnitTest   # Tests
./gradlew assembleDebug       # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # → app/build/outputs/apk/release/app-release.apk (Debug-signiert)
```

## Installation auf dem Smartphone

1. `app-debug.apk` aufs Handy übertragen (Download, USB, Cloud).
2. Datei im Dateimanager antippen.
3. Bei Nachfrage „Installation aus unbekannten Quellen“ für diese App (z. B. Dateien/Chrome) erlauben:
   *Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren*.
4. „Installieren“ tippen. Play Protect warnt ggf., weil die App nicht aus dem Play Store stammt – „Trotzdem installieren“.
5. Alternativ per USB-Debugging: `adb install app-debug.apk`.

Die Debug-Version (Paket `com.ipa.analyzer.debug`) und die Release-Version (`com.ipa.analyzer`) können parallel installiert sein.

## Profilsuche einrichten (optional)

1. Eigenes Instagram-Konto auf **Business** oder **Creator** umstellen und mit einer Facebook-Seite verknüpfen.
2. Unter developers.facebook.com eine App anlegen und die Instagram Graph API hinzufügen.
3. Im Graph API Explorer ein Token mit `instagram_basic`, `pages_show_list`, `pages_read_engagement` erzeugen
   (für Dauerbetrieb ein langlebiges Token).
4. Eigene Instagram-Business-Konto-ID ermitteln: `GET /me/accounts?fields=instagram_business_account`.
5. Token, ID und API-Version in der App unter **Optionen** eintragen.

## Datenexport anfordern

Instagram → Profil → ☰ → Kontenübersicht → Deine Informationen und Berechtigungen → Deine Informationen herunterladen →
„Einige deiner Informationen“ → *Follower und gefolgte Konten* (optional *Inhalte*) → Format **JSON** → Gesamter Zeitraum.
Die ZIP-Datei anschließend in der App unter **Start → Datenexport importieren** auswählen.
