# Changelog

Alle nennenswerten Aenderungen an der Claude/Ankai Assistant Android-App.

## [Unreleased]

### Hinzugefuegt
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
