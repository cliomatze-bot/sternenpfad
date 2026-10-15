# Sternenpfad: Android-APK bauen (Schritt für Schritt)

Dieses Paket enthält alles, damit GitHub aus der App automatisch eine installierbare APK-Datei baut. Du brauchst keinen Android-Studio-Rechner. Du brauchst nur einen kostenlosen GitHub-Zugang und etwa 30 Minuten.

## Was im Paket ist
- `www/index.html`: die fertige App (eine Datei, enthält Astronomie-Bibliothek, Schriftarten und Zeitzonen, läuft ohne Internet; nur die freiwillige Ortssuche braucht eine Verbindung).
- `capacitor.config.json`, `package.json`: Einstellungen für die Android-Hülle (Capacitor).
- `assets/`: App-Symbol, Startbild, Benachrichtigungs-Symbol.
- `.github/workflows/android.yml`: die Bauanleitung für GitHub.

## Vor dem Bauen: zwei Dinge in der App eintragen
1. **PayPal:** Öffne `www/index.html` mit einem Texteditor, suche `var PAYPAL_ME = "";` und trage deinen PayPal.me-Namen ein, zum Beispiel `var PAYPAL_ME = "MeinName";`. Dann öffnen die Spendenknöpfe `paypal.me/MeinName/5EUR` im Browser. Bleibt es leer, zeigt die App „Der Spendenlink folgt“.
2. **Impressum prüfen:** Suche `var IMPRESSUM =` und kontrolliere Name, Anschrift und E-Mail.

## Schritt 1: Eigenen Signierschlüssel erzeugen (einmalig, gut aufbewahren)
Der Schlüssel beweist, dass Updates von dir kommen. **Geht er verloren, kannst du die App nie mehr aktualisieren.** Sichere die Datei und das Passwort an zwei Orten.

Auf einem Rechner mit Java (`keytool`):
```
keytool -genkeypair -v -keystore sternenpfad.jks -alias sternenpfad -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 sternenpfad.jks > schluessel.txt      # auf Mac: base64 -i sternenpfad.jks -o schluessel.txt
```
Ohne Java kannst du diesen Schritt zunächst überspringen. Dann entsteht eine Test-APK mit Wegwerf-Schlüssel (gut zum Ausprobieren, aber nicht für die Verteilung).

## Schritt 2: GitHub einrichten
1. Konto auf github.com anlegen.
2. Neues **privates** Repository „sternenpfad“ erstellen.
3. Alle Dateien dieses Pakets hochladen (Ordner `.github` nicht vergessen).
4. Unter Settings → Secrets and variables → Actions diese vier Secrets anlegen (nur für die echte Version):
   - `KEYSTORE_BASE64`: Inhalt von `schluessel.txt`
   - `KEYSTORE_PASSWORD`: dein Schlüsselspeicher-Passwort
   - `KEY_ALIAS`: `sternenpfad`
   - `KEY_PASSWORD`: dein Schlüssel-Passwort

## Schritt 3: Bauen und herunterladen
1. Reiter **Actions** → „Android-APK bauen“ → **Run workflow**.
2. Nach etwa 10 Minuten erscheint unten bei „Artifacts“ die Datei **Sternenpfad-APK**. Herunterladen und entpacken: `Sternenpfad.apk`.

## Schritt 4: Auf dem Handy installieren
1. APK aufs Handy bringen (E-Mail an dich selbst, Cloud, Kabel).
2. Öffnen. Android fragt, ob Installationen aus dieser Quelle erlaubt sind. Erlauben.
3. Android-Schutzdienste können warnen, weil die App nicht aus dem Play Store kommt. Das ist bei direkter Verteilung normal. Schreibe das auf die Downloadseite.
4. Beim ersten Start fragt die App einmal, ob sie dich um 7 und 18 Uhr erinnern darf.

## Wichtige Hinweise
- **Updates:** Die App aktualisiert sich nicht selbst. Wer eine neue Version will, installiert die neue APK über die alte (mit demselben Schlüssel bleiben die Daten erhalten).
- **Datensicherung:** Die Daten liegen nur auf dem Handy. Wer die App deinstalliert, verliert sie. Darum gibt es unter Mehr → Daten sichern die Sicherung als Datei.
- **Internet-Recht:** Die App hat die Internet-Berechtigung nur für die freiwillige Ortssuche (OpenStreetMap Nominatim). Die Sicherheitsrichtlinie in der App erlaubt Verbindungen ausschließlich zu nominatim.openstreetmap.org. Die Spendenknöpfe öffnen den Browser des Handys.
- **Nicht getestet:** Ich konnte den Bau hier nicht ausführen, weil in meiner Umgebung die Paketquellen gesperrt sind. Der erste Lauf kann deshalb kleine Anpassungen brauchen. Schick mir in dem Fall die Fehlermeldung aus „Actions“, ich korrigiere sie.

## Installation auf dem Handy (für dich und deine Kunden)

- Die Datei `Sternenpfad.apk` ist eine einzige, fertige Datei. Sie läuft ab Android 5.1 (Baujahr etwa 2015) bis zum neuesten Android, auf Handys und Tablets, und braucht danach nichts mehr herunterzuladen.
- Beim ersten Mal fragt Android einmalig nach „Installation aus dieser Quelle zulassen“ (für WhatsApp oder den Browser). Das lässt sich bei Apps außerhalb des Play Store nicht umgehen. Danach genügt „Installieren“ und „Öffnen“.
- Manche Handys zeigen eine Play-Protect-Meldung („Unbekannte App“): „Trotzdem installieren“ antippen. Sie verschwindet erst, wenn die App im Play Store ist.
- WhatsApp verschickt APK-Dateien manchmal nicht oder zeigt sie nicht an. Dann die Datei in eine ZIP-Datei packen oder den Download-Link (z. B. GitHub-Release) schicken.
- Updates: dieselbe Signatur-Datei (Keystore) immer wieder verwenden, sonst lässt sich eine neue Version nicht über die alte installieren.
