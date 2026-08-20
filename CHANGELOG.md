# Changelog

Alle nennenswerten Aenderungen an der Claude/Ankai Assistant Android-App.

## 1.0.10

- Die wirkungslose Auswahl fuer Android-TTS-Engine, Stimme, Vorschau und Sprechgeschwindigkeit
  wurde entfernt. Bei aktiviertem Autoplay stammt die fertige Audiodatei weiterhin von der
  konfigurierten Piper-Stimme der verbundenen Ankaï-Instanz.

## 1.0.8

- Das Texteingabefeld wurde aus dem Homebutton-Assistant entfernt. Der einzige Aktionsbutton
  startet nun die Sprachaufnahme und wechselt waehrend der Aufnahme zu „Abschicken“.

## 1.0.7

- Tippen auf den abgedunkelten Bereich schliesst den Standard-Assistant und stoppt dabei wie
  bisher die automatische Wiedergabe fuer den laufenden Chat.
- Aufnahme und Texteingabe verwenden eine einzige kontextabhaengige Aktion: Im Leerlauf zeigt
  sie das Mikrofon, waehrend einer Aufnahme oder bei eingegebenem Text „Abschicken“ samt
  Sendesymbol. Der redundante separate Senden-Button wurde entfernt.

## 1.0.6

- Interne Antwort-Metadaten (`ChatTitle` und `ProjectContext`) werden weder im Assistant-Overlay
  angezeigt noch an die serverseitige Sprachausgabe weitergereicht.

## 1.0.5

- Gestreamte Antworten werden nicht mehr mit der Android-TTS-Engine gesprochen. Die App fordert
  stattdessen authentifiziert eine kostenfrei auf dem Ankaï-Server mit Piper erzeugte WAV-Datei an
  und spielt sie lokal ab; Schliessen des Overlays stoppt Download und Wiedergabe sofort.

## 1.0.4

- Identische, unmittelbar wiederholte Live-Antwortereignisse werden nur einmal angezeigt und
  nur einmal an den Wiedergabekanal uebergeben.

## 1.0.3

- Der als Android-Standard-Assistent gestartete Homebutton-Dialog zeigt nun die gestreamten
  KI-Antworten und liest neue Antwortsegmente entsprechend der Autoplay-Einstellung vor.
- Beim Schliessen dieses Dialogs wird die Sprachausgabe des Laufs dauerhaft gesperrt, waehrend
  der Ankaï-Lauf wie vorgesehen im Hintergrund weiterarbeitet.

## 1.0.2

- App, Quick-Settings-Tile, Status- und Benachrichtigungstexte in „Ankaï Assistant“ umbenannt.
- Launcher- und Overlay-Symbol auf das Ankaï-Markenlogo umgestellt.

## [Unreleased]

### Behoben
- Direkt nach dem Voice-Start kurzzeitig inaktive Live-Endpunkte werden begrenzt erneut abgefragt,
  damit der Assistant den Lauf nicht vor dessen serverseitiger Registrierung verliert und Antworten
  wieder sichtbar sowie fuer Server-TTS verfuegbar werden.
- Wenn ein Hersteller den Android-Assistenten-Rollendialog ohne sichtbare Auswahl schliesst,
  oeffnet der Setup-Button nun automatisch die sichtbare Seite fuer Standard-Apps. Der bisherige
  Fallback auf die unpassenden Spracheingabe-Einstellungen wurde entsprechend ersetzt.
- Nach einer fehlgeschlagenen Ankaï-Anmeldung bleibt das eingegebene Passwort im Formular erhalten,
  damit die Anmeldung ohne erneute vollständige Eingabe korrigiert oder wiederholt werden kann.
- Die Assistant- und Launcher-Vector-Drawables verwenden gueltige Android-`path`-Elemente,
  sodass AAPT die Debug-APK wieder erfolgreich erzeugen kann.

### Hinzugefuegt
- Ein Button in der Hauptansicht oeffnet direkt den Android-Systemdialog zur Auswahl als
  Standard-Assistent; auf aelteren oder abweichenden Geraeten werden die passenden Einstellungen
  geoeffnet und eine bereits aktive Assistentenrolle wird sichtbar bestaetigt.
- Reproduzierbarer Headless-Emulator-Smoke-Test, der die Debug-APK baut, auf einem
  konfigurierbaren AVD installiert, `MainActivity` startet und Prozess-, Activity- und Crashstatus prueft.
- Ausgewaehlte Android-TTS-Stimmen lassen sich direkt mit einem kurzen Beispielsatz und demselben
  Audiofokus-Verhalten wie echte Antworten vergleichen.
- Netzwerkschicht fuer die Anbindung an eine Ankai-Instanz (`dev.claude.assistant.ankai`):
  `AnkaiEndpoint` (URL-Normalisierung), `AnkaiClient` (Verbindungspruefung, Projektliste,
  Sprachaufnahme, Trennen), `VoiceRequest` (Multipart inkl. `routeProject`/`defaultProjectId`),
  `AnkaiJson` (abhaengigkeitsfreier JSON-Parser).
- Auswertung des NDJSON-Streams von `POST /api/voice` mit Fortschrittsmeldungen und `done`-Ergebnis
  (`sessionId`, `runId`, `transcript`).
- Behandlung unklarer Projektangaben: HTTP 409 bzw. `error`-Event mit `project_unknown` /
  `project_ambiguous` wird als `AnkaiRoutingException` samt Kandidatenliste weitergereicht,
  die App raet nicht selbst.
- Sessioncookie-Wiederverwendung nach der ersten Basic-Auth-Anfrage; `disconnect()` ruft
  `POST /api/auth/logout` und verwirft das Cookie lokal.
- `INTERNET`- und `ACCESS_NETWORK_STATE`-Berechtigung im Manifest.
- Abhaengigkeitsfreie JVM-Testsuite (`jvm-tests/`, `./run-jvm-tests.sh`), lauffaehig ohne Android-SDK.
- Sichere Ablage der Ankai-Verknuepfung: `SecretStore` (android-freie Schnittstelle),
  `AnkaiConnection` (Verknuepfung inkl. Default-Projekt und Sessioncookie, Passwort nicht in `toString`),
  `AnkaiConnectionStore` (speichern/laden/loeschen, Default-Projekt setzen, Cookie uebernehmen)
  und `EncryptedPrefsSecretStore` auf Basis von `EncryptedSharedPreferences` mit Android-Keystore-Masterkey.
- `AnkaiClient.useSessionCookie(...)`, damit ein gespeichertes Cookie nach App-Neustart weitergenutzt wird.
- Abhaengigkeit `androidx.security:security-crypto`.
- Android-freie Zustandslogik fuer die Verknuepfungs-UI (`ConnectionPresenter`/`ConnectionUiState`):
  validiert Zugangsdaten, speichert erst nach erfolgreichem Login samt Projektabruf, verwaltet das
  Default-Projekt und trennt lokal auch bei einem nicht erreichbaren Server.
- MainActivity-Verknuepfungsoberflaeche fuer Instanz-URL, Login, Verbindungsstatus,
  Default-Projektauswahl und Trennen; alle Netzwerkoperationen laufen ausserhalb des UI-Threads.
- Nicht unterstuetzte SVG-Kreisattribute in den Android-VectorDrawables durch kompatible Pfade ersetzt.
- Gemeinsamer Android-freier `VoiceSubmission`-Einstieg fuer Assistant und Overlay: laedt die sichere
  Verknuepfung, wendet das Default-Projekt an, reicht Fortschritt und Routingfehler weiter und speichert
  erneuerte Sessioncookies nach der Sprachuebergabe.
