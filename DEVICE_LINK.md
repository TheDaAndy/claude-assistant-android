# Ankaï-Gerätekopplung (1.2.0)

Der Assistant bleibt erhalten. In der Hauptansicht: Ankaï anmelden und „Gerät koppeln und Freigabe wählen“ öffnen. Dafür ist KEIN Sprach-Standardprojekt erforderlich. Gerätefreigabe und Sprach-Routing sind vollständig getrennt.

## Freigaben wie bei Secrets

- **Global:** alle angemeldeten Nutzer dieser Instanz aus allen Chats, Prompts und Automationen, inklusive projektloser Runs. Eine globale Gerätefreigabe gilt unabhängig von der Berechtigung, globale Secret-Werte zu lesen; der Gerätebesitzer erteilt sie ausdrücklich für sein eigenes Gerät.
- **Mein Nutzer:** alle Prompts und Automationen, die unter dem angemeldeten Nutzer laufen, inklusive projektloser Runs. Maßgeblich ist die Identität des ausführenden Runs, nicht der Projektersteller.
- **Ausgewählte Projekte:** ein bis 50 explizit ausgewählte Projekte und deren aktuell berechtigte Nutzer. Kinderprojekte werden nicht automatisch eingeschlossen.
- **Ein bestimmter Chat:** genau dieser Chat und seine weiteren Runs; normale Chat-/Projektzugriffsrechte werden weiterhin geprüft.

Mehrere passende Geräte werden gemeinsam aufgelistet. Ein Gerät hat jeweils einen dieser Freigabebereiche. „Gerätefreigabe ändern“ erlaubt spätere Änderungen ohne neue Kopplung. Beim Speichern pausiert die App den Kanal; offene alte Aktionen werden verworfen. Danach manuell aktivieren. Verwaltung und Token bleiben ausschließlich beim Gerätebesitzer; Freigabe bedeutet Benutzung, nicht Verwaltung. Ergebnisse/Deduplizierung sind pro anfragendem Nutzer und Chat isoliert.

Bestehende Kopplungen aus 1.1.0 bleiben auf den ursprünglichen Nutzer und das ursprüngliche Projekt beschränkt. Keine automatische Ausweitung auf Projektmitglieder, Nutzer oder global. Erst ausdrückliches Speichern der neuen Freigabe ändert das.

Der Server benötigt Ankaï 0.63.0 oder die erweiterten Geräterouten auf dev. Normale Installationen benötigen HTTPS; ausschließlich Debug-Builds erlauben zusätzlich HTTP für 127.0.0.1 und den Android-Emulatorhost 10.0.2.2. Keine unverschlüsselten Tokens im öffentlichen Netz. Geräte-Token und Ergebnis-Deduplizierung liegen in EncryptedSharedPreferences/Keystore, Android-Backup ist deaktiviert. Die KI erhält eine berechtigte Capability über die Run-API; der Kopplungstoken wird niemals als Secret in einen Prompt oder die Agent-Umgebung übernommen.

## Erste Aktionen

- `device_info`: Hersteller, Modell, Android/API-Version, Sicherheitspatch, Appversion und Akkustand. Keine IMEI, Seriennummer, Kontakte oder privaten App-Daten.
- `settings_snapshot`: Energiesparmodus, Akkuoptimierung für diese App, Overlay-/Benachrichtigungsfreigabe, Flugmodus und automatische Uhrzeit. Keine beliebigen Settings-Schlüssel oder Schreibzugriffe.
- `open_settings`: Anfrage als Benachrichtigung. Antippen öffnet einen lokalen Bestätigungsdialog. Nur `general`, `wifi`, `application` (diese App), `accessibility`, `developer`. Kein beliebiger Intent. `confirmation_required` ist nur eine Anfragebestätigung; es belegt weder Öffnen noch Änderung einer Einstellung. Eine nachträgliche Bestätigung wird noch nicht zum Server zurückgemeldet.

Aktiv ist eine sichtbare Foreground-Service-Benachrichtigung mit „Pausieren“. Keine Wake-Locks, keine automatische Wiederaktivierung nach Boot/Prozesskill. Long-Poll bis 55 Sekunden, Netzfehler-Backoff 5–300 Sekunden. Doze und Hersteller-Energiesparregeln können die Verbindung unterbrechen: keine 24/7-Erreichbarkeitsgarantie und noch keine Stromverbrauchsmessung auf Hardware. In der App pausieren stoppt den Kanal lokal; Widerrufen löscht zusätzlich den Token auf dem Server. Bei Offline-Widerruf bleibt lokal pausiert und die App verlangt erneuten Server-Widerruf. Account-Verbindung erst nach erfolgreichem Gerätewiderruf trennen.

## KI-Zugriff

Run-Authentifizierung wie bei der übrigen Ankaï-Run-API. `GET /api/run/devices` liefert die gemäß Global-/Nutzer-/Projekt-/Chatfreigabe verfügbaren Geräte, auch in projektlosen Runs. Ein Battleships-Chat kann eine globale oder Nutzerfreigabe verwenden; eine Projektfreigabe muss Battleships ausdrücklich enthalten. `GET /api/run/context` dokumentiert den Vertrag.

`POST /api/run/devices/:deviceId/commands` mit `{ "action": "device_info", "clientRequestId": "unique_request_123" }` liefert die Command-ID. Für `open_settings` zusätzlich `target`. Idempotente Request-ID bei Retries wiederverwenden. Ergebnis über `GET /api/run/devices/:deviceId/commands/:commandId` lesen. Maximal zehn offene Aktionen, Ablauf nach zwei Minuten, 30-Sekunden-Lease, Ergebnisse maximal 16 KiB; Historie bei Kanal-/Befehlsaktivität auf 24 Stunden/100 Einträge gekürzt. Eigentümer verwaltet Kopplungen über `GET /api/devices`, `DELETE /api/devices/:deviceId` und `PATCH /api/devices/:deviceId/scope` mit `{scope:{type:"global"|"user"|"projects"|"chat",projectIds?:[],sessionId?:""}}`. Zugriffsgeprüfte, suchbare Auswahl: `GET /api/devices/scope-options?kind=projects|chats&q=...` (100 Treffer, hasMore). Gerät nutzt separat `/api/device-channel/:deviceId/commands` und den Result-Endpunkt; Widerruf sofort wirksam.

## Nächste Rechte-Stufe

Diese Version hat KEINE ADB-/Shell-, APK-Installations-, systemweiten Logcat-, Screenshot- oder Eingaberechte. Für solche Aktionen eignet sich eine explizit freigegebene Shizuku-Anbindung (lokaler ADB-Prozess) oder ein separater ADB-Bridge-Rechner im erreichbaren Netz/VPN. Root/System-App ist dafür nicht erforderlich. Shizuku muss ohne Root typischerweise nach jedem Neustart wieder gestartet werden. Wireless ADB allein überwindet NAT/Netzgrenzen nicht und sollte nicht öffentlich exponiert werden.

Ein alter Geräteadministrator ist kein allgemeiner Fernsteuerungszugang. Device Owner ist für vollständig verwaltete Geräte mit Provisionierung vorgesehen; als System-App installieren verleiht nicht automatisch alle privilegierten/signaturgebundenen Rechte.

Quellen: https://developer.android.com/tools/adb · https://shizuku.rikka.app/introduction/ · https://shizuku.rikka.app/guide/setup/ · https://developer.android.com/work/device-admin · https://source.android.google.cn/docs/core/permissions/perms-allowlist · https://developer.android.com/training/monitoring-device-state/doze-standby
