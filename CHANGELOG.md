# 1.10.0

- Persönliche Mailvorschläge zeigen konkrete Anhänge vor Freigabe. Authentifizierte Bildvorschau mit Größen- und SHA-256-Prüfung; PDF-Vorschau der ersten Seite.

# Changelog

## 1.12.0 (Build 28)
- Optional once-per-boot Wireless Debugging activation using a separately granted WRITE_SECURE_SETTINGS permission; requires existing channel/ADB consent and Wi-Fi, respects later manual disabling.


## 1.11.3 (Build 27)
- Preserve the actual verified local ADB UID, accepting rooted adbd without silently granting root; require matching local consent before commands and recovery.


## 1.11.2 (Build 26)
- Restore an already paired and consented local Wireless Debugging connection inside the active device service after process restart and network return, with bounded discovery/backoff and pause/revocation checks. Wait for credential storage unlock before inbox recovery.


## 1.11.1 (Build 25)
- Pair Wireless Debugging from an inline notification reply while Android Settings stays open, with local port discovery and automatic connection. Split screen is no longer required.


## 1.11.0 (Build 24)
- Replace the external Shizuku dependency with locally paired TLS Wireless Debugging, encrypted ADB identity storage, explicit on-device consent, UID verification and bounded shell-v2 execution.


Alle nennenswerten Aenderungen an der Claude/Ankai Assistant Android-App.

## 1.9.0

- Native Android-Benachrichtigungen für ungelesene Einträge aus `/api/notifications`, unabhängig von Gerätekopplung/Fernsteuerung. In Einstellungen lokal einschaltbar, inklusive Android-Berechtigungsabfrage und eigenem Benachrichtigungskanal.
- Sichtbarer Hintergrunddienst prüft alle 30 s; persistenter 15-Minuten-Systemjob als Rückfallebene, Wiederaufnahme nach Boot/App-Update. Ohne Wake-Lock; Doze, Force-Stop, Netz- und OEM-Beschränkungen können die Zustellung verzögern. Serverseitige Liste umfasst aktuell die neuesten 50 Einträge.
- Verschlüsselte, kontogetrennte Zustellbelege verhindern erneute Meldungen nach Neustart oder Wegwischen; fehlgeschlagene Abrufe werden erneut versucht. Gelesene Meldungen lösen keine neue Benachrichtigung aus. Trennen schaltet die Funktion ab und entfernt angezeigte Hinweise.
- Tippen öffnet das verknüpfte Ankaï-Ziel; externe URLs/Intent-Schemata werden abgefangen, Kontoänderungen geprüft und Sperrbildschirm-Inhalte als privat markiert. Keine Zugangsdaten in Browser-Links.

## 1.8.0

- Native Entscheidungs-Inbox als App-Startseite mit offenen Karten und Verlauf; aus Einstellungen, Assist-Geste und Quick-Tile erreichbar. Ziele, Agenten und Konnektoren lassen sich über die verknüpfte Ankaï-Oberfläche verwalten.
- Fragen beantworten, konkrete Aktionen freigeben/ablehnen, Änderungswünsche schreiben oder als Audio aufnehmen; Ergebnisse als hilfreich/nicht hilfreich bewerten. Risiko, Konfidenz, Quellen, Ablaufzeit und externe Auswirkungen bleiben sichtbar.
- Jede Freigabe/Anpassung bindet die angezeigte Vorschlagsversion. Schreibanfragen werden bei Netzfehlern nicht automatisch wiederholt. Sprachfeedback erteilt keine Aktionsfreigabe. Kontocookies werden vor Schreibaufrufen lesend erneuert und ausschließlich verschlüsselt gespeichert.
- Neue androidfreie Karten-/Multipartmodelle und HTTP-Vertragstests; 151 JVM-Tests. API35-Emulator gegen isolierten echten Ankaï-Backendstand geprüft; STT-Antwort im Test deterministisch, keine Aussage zur Mikrofon-/STT-Qualität auf echten Handys.

## 1.7.1

- Abgelaufene/abgewiesene Sitzungscookies bei lesenden Konto-, Projekt-, Verlaufs- und Live-Anfragen durch genau einen erneuten Versuch mit gespeicherten Zugangsdaten ersetzen. Neue Sitzung wird bei erfolgreichem Verbindungs-Refresh verschlüsselt gespeichert; Projektwahl und Gerätekopplung bleiben erhalten. Keine automatische Wiederholung von Uploads oder schreibenden Aktionen, keine Wiederholungsschleife bei falschen Zugangsdaten.
- Veralteten Hauptansicht-Hinweis zur manuellen Aktivierung nach Neustart korrigiert: automatische Wiederaufnahme, Akku-Ausnahme und Android-Grenzen erklärt.

## 1.7.0

- Aktivierten Gerätekanal nach Boot/erstem Entsperren und eigenem APK-Update wiederherstellen; persistente 15-Minuten-JobScheduler-Rückfallebene respektiert Pause und Widerruf. Android kann Starts/Jobs verzögern oder blockieren.
- Netzwerk-Rückkehr unterbricht Reconnect-Wartezeit; Fehler-Backoff auf maximal30s statt5min begrenzt. Fehlerhafte Aktionen und HTTP404/409 bei Ergebnissen deaktivieren nicht mehr die Kopplung; nur bestätigte Auth-Ablehnung des Poll-Kanals stoppt sie. Ungewisse Befehle werden nicht erneut ausgeführt.
- Unerwartetes Dienstende sendet keine falsche dauerhafte Rechte-Deaktivierung und bricht nicht pauschal Installationssessions ab. Explizite Pause bleibt wirksam.
- Lokaler Button für Androids Akku-Ausnahme zur dauerhaften Geräteverbindung; kein permanenter Wake-Lock, keine Force-Stop-/Gerätesperren-Umgehung und keine garantierte 24/7-Verbindung.

## 1.6.1

- Gerätekanal bleibt nach systembedingtem Dienstende aktiviert und verwendet START_STICKY für Androids Wiederherstellung. Bewusstes Pausieren und widerrufene Kopplungen bleiben deaktiviert. Force-Stop und Android/OEM-Startbeschränkungen bleiben Grenzen.

## 1.6.0

- Eigenständige lokale Freigabe für APK-Installationsanfragen, Android-Erlaubnis und sichtbare Bestätigungsbenachrichtigung; unabhängig von Bedienungshilfe/Shizuku.
- Geschützter Einzel-APK-Transfer mit Größen-/SHA256-Prüfung und Android-PackageInstaller-Session. Jede Installation verlangt lokale Android-Zustimmung; echte System-Ergebnisse statt bloßer Übergabebestätigung.
- Verschlüsselte Ergebnis-Outbox, einmalige Befehlsausführung, Abbruch offener Sessions beim Pausieren/Freigabewechsel soweit möglich; keine stille Installation, Deinstallation oder automatische Dialogbestätigung.

## 1.5.0

- Live-Verfügbarkeit während des aktiven Gerätekanals: Display an/aus, Sperrbildschirm/Gerätesperre und näherungsweise kürzlich erkannte UI-Interaktion. Änderungen und30s-Heartbeat ohne permanenten Wake-Lock, keine Bildschirmtexte im Status.
- Separater Statuskanal, frische Verfügbarkeit/Handlungsgrund über die Ankaï-Geräte-API; dunkle und gesperrte Geräte werden lokal für UI-Aktionen abgewiesen. Keine automatische Entsperrung.
- UI erklärt die begrenzte Aktivitätserkennung einschließlich Automationsereignissen. Neue reine JVM-Zeitmodelltests und separate native API35-Presence-Regression; keine Test-Hooks im App-APK.

## 1.4.0

- Rootlose Fernsteuerung über ausdrücklich aktivierte Android-Bedienungshilfe und separaten lokalen Ankaï-Grant.
- Bildschirm lesen, Screenshot (Android11+), Tippen/Wischen/Text, Home/Zurück und App-Start ohne ADB/Shizuku.
- Optionale Wiederaufnahme bei erneuter Android-Dienstbindung; Pause schaltet sie aus. Gesperrte/geschützte Ansichten und lokale Berechtigungs-/Installationsbestätigungen bleiben eingeschränkt.

## 1.3.0

- Optionaler Maximalmodus über Shizuku mit tatsächlichen ADB-/Shell- oder Root-Rechten; einmalige Zustimmung am Handy gebunden an die Rechtestufe.
- Bestehende Global-/Nutzer-/Projekt-/Chatfreigaben gelten auch für privilegierte Befehle. Kein Geräte-Token im KI-Prompt.
- Zeit-/Ausgabelimits, keine automatische Wiederholung privilegierter Aktionen, Abbruch beim Pausieren/Entzug. Shizuku-Einrichtungsseite mit WLAN-Debugging-Anleitung.

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
