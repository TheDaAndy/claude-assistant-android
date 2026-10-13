# Ankaï-Gerätekopplung (1.1.0)

Der Assistant bleibt erhalten. In der Hauptansicht: Ankaï anmelden, ein Default-Projekt wählen, „Mit ausgewähltem Projekt koppeln“ bestätigen und Gerätezugriff einschalten. Das gewählte Projekt ist die feste Freigabe dieser Kopplung; spätere Änderungen des Sprach-Default-Projekts ändern sie nicht. Für ein anderes Projekt zuerst widerrufen und neu koppeln.

Der Server benötigt die neuen Geräterouten (Ankaï dev, noch kein Produktionsrelease). Normale Installationen benötigen HTTPS; ausschließlich Debug-Builds erlauben zusätzlich HTTP für 127.0.0.1 und den Android-Emulatorhost 10.0.2.2. Keine unverschlüsselten Tokens im öffentlichen Netz. Geräte-Token und Ergebnis-Deduplizierung liegen in EncryptedSharedPreferences/Keystore, Android-Backup ist deaktiviert. Der Token berechtigt ausschließlich zum eigenen Gerätekanal und kann keine Account-API nutzen.

## Erste Aktionen

- `device_info`: Hersteller, Modell, Android/API-Version, Sicherheitspatch, Appversion und Akkustand. Keine IMEI, Seriennummer, Kontakte oder privaten App-Daten.
- `settings_snapshot`: Energiesparmodus, Akkuoptimierung für diese App, Overlay-/Benachrichtigungsfreigabe, Flugmodus und automatische Uhrzeit. Keine beliebigen Settings-Schlüssel oder Schreibzugriffe.
- `open_settings`: Anfrage als Benachrichtigung. Antippen öffnet einen lokalen Bestätigungsdialog. Nur `general`, `wifi`, `application` (diese App), `accessibility`, `developer`. Kein beliebiger Intent. `confirmation_required` ist nur eine Anfragebestätigung; es belegt weder Öffnen noch Änderung einer Einstellung. Eine nachträgliche Bestätigung wird noch nicht zum Server zurückgemeldet.

Aktiv ist eine sichtbare Foreground-Service-Benachrichtigung mit „Pausieren“. Keine Wake-Locks, keine automatische Wiederaktivierung nach Boot/Prozesskill. Long-Poll bis 55 Sekunden, Netzfehler-Backoff 5–300 Sekunden. Doze und Hersteller-Energiesparregeln können die Verbindung unterbrechen: keine 24/7-Erreichbarkeitsgarantie und noch keine Stromverbrauchsmessung auf Hardware. In der App pausieren stoppt den Kanal lokal; Widerrufen löscht zusätzlich den Token auf dem Server. Bei Offline-Widerruf bleibt lokal pausiert und die App verlangt erneuten Server-Widerruf. Account-Verbindung erst nach erfolgreichem Gerätewiderruf trennen.

## KI-Zugriff

Run-Authentifizierung wie bei der übrigen Ankaï-Run-API. `GET /api/run/devices` liefert nur eigene, ausdrücklich für das aktuelle Projekt gekoppelte Geräte. Ein Battleships-Chat benötigt eine Kopplung mit dem Battleships-Projekt. `GET /api/run/context` dokumentiert den Vertrag.

`POST /api/run/devices/:deviceId/commands` mit `{ "action": "device_info", "clientRequestId": "unique_request_123" }` liefert die Command-ID. Für `open_settings` zusätzlich `target`. Idempotente Request-ID bei Retries wiederverwenden. Ergebnis über `GET /api/run/devices/:deviceId/commands/:commandId` lesen. Maximal zehn offene Aktionen, Ablauf nach zwei Minuten, 30-Sekunden-Lease, Ergebnisse maximal 16 KiB; Historie bei Kanal-/Befehlsaktivität auf 24 Stunden/100 Einträge gekürzt. Eigentümer kann Kopplungen über `GET/DELETE /api/devices/:deviceId` verwalten. Gerät nutzt separat `/api/device-channel/:deviceId/commands` und den Result-Endpunkt; Widerruf sofort wirksam.

## Nächste Rechte-Stufe

Diese Version hat KEINE ADB-/Shell-, APK-Installations-, systemweiten Logcat-, Screenshot- oder Eingaberechte. Für solche Aktionen eignet sich eine explizit freigegebene Shizuku-Anbindung (lokaler ADB-Prozess) oder ein separater ADB-Bridge-Rechner im erreichbaren Netz/VPN. Root/System-App ist dafür nicht erforderlich. Shizuku muss ohne Root typischerweise nach jedem Neustart wieder gestartet werden. Wireless ADB allein überwindet NAT/Netzgrenzen nicht und sollte nicht öffentlich exponiert werden.

Ein alter Geräteadministrator ist kein allgemeiner Fernsteuerungszugang. Device Owner ist für vollständig verwaltete Geräte mit Provisionierung vorgesehen; als System-App installieren verleiht nicht automatisch alle privilegierten/signaturgebundenen Rechte.

Quellen: https://developer.android.com/tools/adb · https://shizuku.rikka.app/introduction/ · https://shizuku.rikka.app/guide/setup/ · https://developer.android.com/work/device-admin · https://source.android.google.cn/docs/core/permissions/perms-allowlist · https://developer.android.com/training/monitoring-device-state/doze-standby
