# API-Spec — sotf-control

Base-URL nach Deploy:
`https://europe-west3-<PROJECT>.cloudfunctions.net/sotf-control`

## Auth

Jeder Request:
```
Authorization: Bearer <SOTF_API_TOKEN>
```
Fehlt/falsch → `401 {"error":"unauthorized"}`.

Der Token liegt als Secret in der Function (Env-Var `API_TOKEN`) und in
der App (EncryptedSharedPreferences). Kein Token in Git.

## Konventionen

- Alles JSON, UTF-8.
- Zeitstempel: RFC3339 UTC (`2026-08-21T19:30:00Z`).
- Fehler: `{"error": "<slug>", "detail": "<text>"}` + passender Statuscode.
- Routing über den Pfad nach der Function-URL (`/server/status` etc.).

---

## Server (spielunabhängig)

### `GET /servers`
Liste der konfigurierten Server. Die App zeigt sie im Startup-Screen.

```json
{
  "servers": [
    {
      "id": "sotf-main",
      "name": "Kevkes SotF",
      "game": "sons-of-the-forest",
      "zone": "europe-west3-a",
      "instance": "sotf-server",
      "logo": "sotf"
    }
  ]
}
```

### `GET /server/status?id=sotf-main`

```json
{
  "id": "sotf-main",
  "state": "RUNNING",
  "externalIp": "34.179.227.158",
  "lastStart": "2026-08-21T17:36:29Z",
  "lastStop": "2026-08-21T17:22:38Z",
  "uptimeSeconds": 7200,
  "gameReady": true,
  "playersOnline": 2,
  "idleShutdownIn": 640
}
```

`state`: `RUNNING` | `TERMINATED` | `STAGING` | `STOPPING` | `UNKNOWN`
`gameReady`: Container healthy **und** Welt geladen (Agent meldet das).
`idleShutdownIn`: Sekunden bis Auto-Shutdown, `null` wenn Spieler online.

### `POST /server/start`
```json
{"id": "sotf-main"}
```
→ `202 {"state":"STAGING","message":"Server faehrt hoch, ca. 3 Minuten."}`

### `POST /server/stop`
```json
{"id": "sotf-main", "force": false}
```
`force: false` → lehnt ab wenn Spieler online (`409 players_online`).
`force: true` → stoppt trotzdem.
→ `202 {"state":"STOPPING"}`

---

## Backups

### `GET /backups?id=sotf-main`
```json
{
  "backups": [
    {
      "name": "2026-08-21_1930",
      "createdAt": "2026-08-21T19:30:00Z",
      "sizeBytes": 214000,
      "slot": 1,
      "worldName": "Kevin_ist_Kek",
      "gameDays": 7,
      "source": "manual"
    }
  ]
}
```
`source`: `manual` | `auto` | `pre-restore`

### `POST /backups/create`
```json
{"id": "sotf-main", "label": "vor dem Bunker"}
```
→ `202 {"job":"<jobId>"}` — Agent führt aus, Status via `GET /jobs/<jobId>`.

### `POST /backups/restore`
```json
{"id": "sotf-main", "name": "2026-08-21_1930"}
```
Erstellt **immer** vorher ein `pre-restore`-Backup.
→ `202 {"job":"<jobId>"}`

### `POST /backups/delete`
```json
{"id": "sotf-main", "name": "2026-08-21_1930", "confirm": true}
```
Ohne `confirm: true` → `400 confirmation_required`.
→ `200 {"deleted":"2026-08-21_1930"}`

---

## Resources

### `GET /metrics?id=sotf-main&range=1h`
`range`: `15m` | `1h` | `6h` | `24h` | `7d`

```json
{
  "current": {"cpuPercent": 42.3, "memPercent": 61.0, "memUsedMb": 9840, "memTotalMb": 16000},
  "series": [
    {"t": "2026-08-21T19:00:00Z", "cpu": 41.2, "mem": 60.1},
    {"t": "2026-08-21T19:00:30Z", "cpu": 43.7, "mem": 60.4}
  ]
}
```

Auflösung wird serverseitig gebucketed: 15m→30 s, 1h→1 min, 6h→5 min,
24h→15 min, 7d→1 h.

---

## Billing

### `GET /billing?id=sotf-main&range=month`
`range`: `day` | `week` | `month` | `quarter` | `year` | `last-month` |
`custom` (+ `from=`&`to=`)

```json
{
  "range": {"from": "2026-08-01T00:00:00Z", "to": "2026-08-31T23:59:59Z"},
  "totalEur": 18.42,
  "uptimeSeconds": 390600,
  "hourlyRateEur": 0.17,
  "perPlayer": [
    {
      "steamId": "76561198104125441",
      "name": "Kevke Maradtinio",
      "lastIp": "84.134.28.241",
      "sessionSeconds": 172800,
      "shareEur": 11.30
    },
    {
      "steamId": "76561198179125691",
      "name": "Host",
      "lastIp": "91.20.11.4",
      "sessionSeconds": 93600,
      "shareEur": 7.12
    }
  ],
  "unattributedEur": 0.00
}
```

**Kostenaufteilung:** Die Laufzeit wird in Intervalle zerlegt, an denen
sich die Menge der Online-Spieler ändert. Jedes Intervall wird durch die
Anzahl gleichzeitig Anwesender geteilt.

```
t0───────t1──────────t2────────t3
  nur A     A + B       B + C
  60 min    120 min     30 min
  A: 60     A: 60       B: 15
            B: 60       C: 15
```
Zeit ohne Spieler (Boot, Idle vor Shutdown) landet in `unattributedEur`.

---

## Jobs

Alles was der Agent ausführt, läuft asynchron.

### `GET /jobs/<jobId>`
```json
{
  "id": "j_a1b2c3",
  "kind": "backup.create",
  "state": "done",
  "createdAt": "2026-08-21T19:30:00Z",
  "finishedAt": "2026-08-21T19:30:04Z",
  "result": {"name": "2026-08-21_1930", "sizeBytes": 214000},
  "error": null
}
```
`state`: `queued` | `running` | `done` | `failed`

---

## Sons of the Forest

### `GET /sotf/worlds?id=sotf-main`
```json
{
  "activeSlot": 1,
  "slots": [
    {"slot": 1, "worldName": "Kevin_ist_Kek", "gameDays": 7, "sizeBytes": 206325,
     "lastSaved": "2026-08-21T19:33:00Z"},
    {"slot": 2, "worldName": null, "gameDays": 0, "sizeBytes": 144603,
     "lastSaved": "2026-08-21T19:48:00Z"}
  ]
}
```

### `POST /sotf/worlds/activate`
```json
{"id": "sotf-main", "slot": 2}
```
Schreibt `SaveSlot` in `dedicatedserver.cfg`, Container-Restart.
→ `202 {"job":"<jobId>"}`

### `POST /sotf/worlds/upload`
`multipart/form-data`: `file` = ZIP im SotF-Save-Format, `slot` = Ziel.
Agent validiert die Struktur (muss `SaveData.zip` enthalten), legt
vorher ein Backup an.
→ `202 {"job":"<jobId>"}`

### `GET /sotf/players?id=sotf-main`
```json
{
  "online": [
    {"steamId": "76561198104125441", "name": "Kevke Maradtinio",
     "ip": "84.134.28.241", "clientId": 2,
     "connectedAt": "2026-08-21T18:10:00Z"}
  ]
}
```

### `GET /sotf/items`
Statischer Katalog, aus `sotf/android/app/src/main/assets/sotf_items.json`.
```json
{
  "items": [
    {"id": 356, "name": "Modern Axe", "tags": ["weapon","tool","melee"]},
    {"id": 379, "name": "Crafted Spear", "tags": ["weapon","melee","craftable"]}
  ]
}
```

### `POST /sotf/give`
```json
{"id": "sotf-main", "steamId": "76561198104125441", "itemId": 356, "count": 1}
```
**⚠️ Blocker B1** — bis der Konsolen-Weg geklärt ist, antwortet der
Endpoint mit:
```json
{
  "mode": "manual",
  "command": "give 356 1",
  "hint": "Konsole mit F1 oeffnen und Befehl eingeben"
}
```
Die App zeigt den Befehl mit Copy-Button. Sobald ein echter Konsolen-Kanal
existiert, liefert der Endpoint stattdessen `202 {"job":...}`.

### `POST /sotf/teleport`
```json
{"id": "sotf-main", "steamId": "765...441", "target": {"kind": "player", "steamId": "765...691"}}
{"id": "sotf-main", "steamId": "765...441", "target": {"kind": "xyz", "x": 100.0, "y": 20.0, "z": -300.0}}
{"id": "sotf-main", "steamId": "765...441", "target": {"kind": "poi", "poi": "cave-1"}}
```
Gleicher Blocker, gleiche `mode: "manual"`-Antwort.

### `GET /sotf/config?id=sotf-main`
Liefert die aktuelle `dedicatedserver.cfg` plus ein Schema, damit die App
generisch Widgets rendern kann.
```json
{
  "config": { "...": "aktuelle cfg 1:1" },
  "schema": [
    {"key": "GameSettings.Structure.Damage", "type": "bool", "label": "Strukturschaden",
     "hint": "Aus = Basis unzerstoerbar", "appliesToExistingWorld": true},
    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemyHealth", "type": "enum",
     "values": ["Low","Normal","High","VeryHigh"], "label": "Gegner-Leben",
     "appliesToExistingWorld": false}
  ]
}
```
`appliesToExistingWorld: false` → App zeigt einen Warnhinweis, dass die
Einstellung erst bei einer neuen Welt greift.

### `POST /sotf/config`
```json
{"id": "sotf-main", "changes": {"GameSettings.Structure.Damage": false}, "restart": true}
```
Agent legt Backup an, patcht die cfg, startet den Container neu.
→ `202 {"job":"<jobId>"}`
