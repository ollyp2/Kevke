# DEVBOARD — SotF Server Control App

Lebendes Dokument. Status wird bei jeder Session aktualisiert.

## Vision

Android-App zum Steuern beliebiger Game-Server auf GCE. Server-agnostisch
im Kern (An/Aus, Backup, Resources, Billing), spielspezifische Module
darüber (aktuell: Sons of the Forest).

## Architektur

```
┌─────────────────┐     HTTPS + Bearer      ┌──────────────────────┐
│  Android App    │ ──────────────────────► │  Cloud Function      │
│  (Kotlin/       │ ◄────────────────────── │  sotf-control        │
│   Compose)      │        JSON             │  (Gen2, python312)   │
└─────────────────┘                         └──────────┬───────────┘
                                                       │
                        ┌──────────────────────────────┼──────────────┐
                        │                              │              │
                        ▼                              ▼              ▼
              ┌──────────────────┐         ┌────────────────┐  ┌────────────┐
              │ Compute Engine   │         │  Firestore     │  │  GCS       │
              │ API (start/stop) │         │  (sessions,    │  │  (backups) │
              └──────────────────┘         │   metrics)     │  └────────────┘
                                           └────────▲───────┘
                                                    │ push alle 30 s
                                           ┌────────┴───────┐
                                           │  VM-Agent      │
                                           │  (Python,      │
                                           │   systemd)     │
                                           │  - CPU/RAM     │
                                           │  - Log-Parse   │
                                           │    (Steam-IDs) │
                                           │  - RCON-ähnl.  │
                                           │    Konsole     │
                                           └────────────────┘
```

**Warum ein VM-Agent?** Die App kann die VM nicht direkt erreichen (kein
offener Port, wechselnde IP). Der Agent pusht Metriken und Session-Events
nach Firestore; die Function liest von dort. Für Aktionen, die auf der VM
laufen müssen (Item geben, Teleport, World-Switch), nimmt der Agent
Kommandos aus einer Firestore-Queue entgegen und quittiert sie.

## Komponenten & Status

| # | Komponente | Pfad | Status |
|---|---|---|---|
| 1 | DEVBOARD | `sotf/DEVBOARD.md` | ✅ |
| 2 | API-Spec | `sotf/api-spec.md` | ✅ |
| 3 | Backend Cloud Function | `sotf/backend/` | ✅ Code fertig, ⏳ nicht deployed |
| 4 | VM-Agent | `sotf/agent/` | ✅ Code fertig, ⏳ nicht installiert |
| 5 | Android-App | `sotf/android/` | ✅ Code fertig, ⏳ nicht gebaut |
| 6 | Handoff-Doku | `sotf/HANDOFF.md` | ✅ |

## Feature-Matrix

### Server-Funktionen (spielunabhängig)

| Feature | Backend | Agent | App-UI | Verifiziert |
|---|---|---|---|---|
| An/Aus (großer Runder Knopf) | ✅ | — | ✅ | ⏳ |
| Status-Lampe (RUNNING/TERMINATED/STAGING) | ✅ | — | ✅ | ⏳ |
| Backup: jetzt speichern | ✅ | ✅ | ✅ | ⏳ |
| Backup: restore | ✅ | ✅ | ✅ | ⏳ |
| Backup: löschen (mit Abfrage) | ✅ | ✅ | ✅ | ⏳ |
| Resources: CPU/RAM live + Historie | ✅ | ✅ | ✅ | ⏳ |
| Billing: Total nach Zeitraum | ✅ | — | ✅ | ⏳ |
| Billing: pro Spieler (session-basiert) | ✅ | ✅ | ✅ | ⏳ |
| Multi-Server (variable IP) | ✅ | — | ✅ | ⏳ |

### Sons of the Forest

| Feature | Backend | Agent | App-UI | Verifiziert |
|---|---|---|---|---|
| Change World (Slots) | ✅ | ✅ | ✅ | ⏳ |
| Upload World | ✅ | ✅ | ✅ | ⏳ |
| Give Item (Player-ID + Item-Liste) | ✅ | ⚠️ | ✅ | ⏳ |
| Teleport Player→Player | ✅ | ⚠️ | ✅ | ⏳ |
| Teleport Player→XYZ | ✅ | ⚠️ | ✅ | ⏳ |
| Teleport Player→POI | ✅ | ⚠️ | ✅ | ⏳ |
| Change Config (alle Attribute) | ✅ | ✅ | ✅ | ⏳ |

⚠️ = **Blocker, siehe unten.**

## Bekannte Blocker / Offene Fragen

### B1 — Konsolen-Zugriff auf den Dedicated Server (KRITISCH)
Give Item, Teleport und alle Live-Commands brauchen einen Weg, Befehle in
die laufende Server-Instanz zu schicken. Der SotF-Dedi hat **kein RCON**.
Optionen:
- **(a)** Konsole nur clientseitig (F1 im Spiel) → App kann es nicht.
  Realistisch: App zeigt die Befehle zum Abtippen an.
- **(b)** RedLoader-Mod auf dem Server → bringt eine Server-Konsole mit,
  aber ändert das Docker-Image und ist ein Eingriff.
- **(c)** stdin des Container-Prozesses füttern (`docker attach` /
  `docker exec`) → ungetestet, ob der Dedi stdin-Kommandos annimmt.
**→ Zu klären mit Kevke. Bis dahin baut die App (a): Command-Builder mit
Copy-Button.** Agent hat den Hook schon drin (`console_exec`), sobald (b)
oder (c) geht, wird nur die eine Funktion ausgetauscht.

### B2 — Spieler-Identität für Billing
Der Container loggt bei Connect: `Steam auth successful for client N with
steam id <17-stellig>, username <Name>` und die Endpoint-IP. Der Agent
parst das. **Frage an Kevke:** Abrechnung nach Steam-ID (stabil) statt IP
(wechselt bei DSL-Reconnect)? Empfehlung: Steam-ID als Schlüssel,
IP nur als Zusatzinfo.

### B3 — Billing-Kostenquelle
Zwei Wege:
- **(a)** Cloud Billing API (echte Kosten, braucht BigQuery-Export +
  Berechtigungen, ~1 Tag Verzögerung)
- **(b)** Selbst rechnen: Laufzeit × fester €/h-Satz (sofort, exakt genug)
**→ Implementiert ist (b)** mit konfigurierbarem Stundensatz. (a) kann
später ergänzt werden.

### B4 — Auth für die App
Aktuell: statischer Bearer-Token in der App gespeichert (Shared
Preferences, verschlüsselt). Reicht für privaten Gebrauch.
**Nicht** produktionsreif für Fremde. Upgrade-Pfad: Firebase Auth +
Google Sign-In, Function prüft ID-Token.

### B5 — Ausschalt-Link
Bisher gibt es nur `start_server`. Der Stop-Endpoint ist im neuen Backend
enthalten (`POST /server/stop`), muss aber mit deployed werden.

## Endpunkte, die Kevke verknüpfen muss

Das sind die Stellen, an denen ich nicht weiterkomme ohne dich:

1. **Backend deployen** — `sotf/backend/deploy.sh` ausführen (braucht deine
   gcloud-Credentials). Liefert die Base-URL für die App.
2. **Service-Account-Rollen** — die Function braucht
   `roles/compute.instanceAdmin.v1` (start/stop) und
   `roles/datastore.user` (Firestore).
3. **Firestore aktivieren** — einmalig im Projekt, Native Mode.
4. **GCS-Bucket anlegen** — für Backups, Name in `deploy.sh` eintragen.
5. **Agent installieren** — `sotf/agent/install.sh` auf der VM.
6. **Agent-Service-Account** — VM braucht Firestore-Schreibrecht. Aktuell
   hat die VM eingeschränkte Scopes (siehe Session-Historie!). Entweder
   Scopes erweitern (VM-Neustart nötig) oder Agent authentifiziert sich
   mit einem Key-File.
7. **App bauen** — Android Studio auf deinem Rechner, oder ich gebe dir
   eine GitHub-Action die eine APK baut.
8. **Token setzen** — gemeinsamer Secret zwischen App, Function, Agent.
9. **B1 entscheiden** — welchen Weg für Konsolen-Commands.
10. **B2 bestätigen** — Steam-ID oder IP als Billing-Schlüssel.

## Design-Regeln (verbindlich)

- **Nur Line-Art-Icons**, keine Emoji, keine gefüllten Flächen-Icons.
  Stroke-Width 1.5–2, `currentColor`.
- **Variable Hintergrund- und Akzentfarbe**, vom User einstellbar.
  Defaults: BG `#121212` (dark) / `#FAFAFA` (light), Accent `#7A8B7F`
  (gedämpftes Salbeigrün — bewusst kein Indigo/Violett/Cyan-Gradient).
- **Schlicht.** Keine Gradients, keine Glassmorphism, keine Neon-Glows.
- Typografie: System-Sans, ein Schriftschnitt, Größe trägt die Hierarchie.
- Ecken: 12 dp Radius durchgehend. Ein Schatten-Level, subtil.

## Changelog

- **2026-08-21** — DevBoard angelegt. Backend, Agent, App-Grundgerüst und
  API-Spec geschrieben. Alles ungetestet, wartet auf Deploy.
