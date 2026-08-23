# DEVBOARD — SotF Server Control App

Lebendes Dokument. Status wird bei jeder Session aktualisiert.

## Leitprinzip

**Alles läuft über die Cloud Function. Eine URL, ein Token, Actions als
Query-Parameter.** Jede Aktion, die die App auslöst, ist ein Link, den man
genauso in den Browser tippen könnte.

Kein Firestore, keine Job-Queue, nichts auf der VM installiert — solange
es ohne geht.

## Architektur

```
┌──────────────┐   GET ?token=…&action=…   ┌────────────────────┐
│ Android-App  │ ────────────────────────► │  sotf-control      │
│              │ ◄──────────────────────── │  (Cloud Function)  │
└──────────────┘        JSON / Text        └─────────┬──────────┘
                                                     │
                                    ┌────────────────┴──────────────┐
                                    ▼                               ▼
                          ┌──────────────────┐          ┌────────────────────┐
                          │ Compute Engine   │          │ UDP :27016         │
                          │ API              │          │ Steam-Query an die │
                          │ start/stop/get   │          │ laufende Instanz   │
                          └──────────────────┘          └────────────────────┘
```

**Warum die Steam-Query?** Die Compute API meldet `RUNNING`, sobald die VM
Strom hat — aber die Welt lädt danach noch rund drei Minuten. Ein UDP-Paket
an den Query-Port beantwortet die Frage, die wirklich zählt: *kann ich
jetzt joinen?* Nebenbei liefert es Servername und Spielerzahl.

## Actions

| URL | Wirkung |
|---|---|
| `?token=…&action=start` | VM starten |
| `?token=…&action=stop` | VM stoppen (lehnt ab wenn Spieler online) |
| `?token=…&action=stop&force=1` | trotzdem stoppen |
| `?token=…&action=status` | Zustand, IP, Spieler, Laufzeit |
| `?token=…&action=backup&label=…` | Disk-Snapshot anlegen |
| `?token=…&action=backups` | Snapshots auflisten |
| `?token=…&action=restore&name=…` | Boot-Disk auf Snapshot zurücksetzen |
| `?token=…&action=delete_backup&name=…` | Snapshot löschen |
| `?token=…&action=billing&range=month` | Laufzeit und Kosten im Zeitraum |

Ohne `format=json` bzw. `Accept: application/json` kommt eine lesbare
Zeile zurück — genau wie beim alten `sotf-start-trigger`, damit die Links
im Browser und im Chat brauchbar bleiben.

## Komponenten & Status

| # | Komponente | Pfad | Status |
|---|---|---|---|
| 1 | Control Function | `sotf/function/` | ✅ deployed, im Betrieb |
| 2 | Android-App | `sotf/android/` | ✅ gebaut und installiert |
| 3 | DEVBOARD | `sotf/DEVBOARD.md` | ✅ |
| 4 | Handoff-Doku | `sotf/HANDOFF.md` | ✅ |
| 5 | Gesamtstand + To-dos | `sotf/STATE.md` | ✅ |

**Der vollständige Stand inklusive der offenen Punkte steht in
[`STATE.md`](STATE.md).** Dort auch die Liste der Dinge, die uns je eine
ganze Runde gekostet haben — vor jeder Änderung einmal überfliegen.

## Feature-Matrix

| Feature | Function | App-UI | Verifiziert |
|---|---|---|---|
| An/Aus (großer runder Knopf) | ✅ | ✅ | ✅ |
| Status-Lampe | ✅ | ✅ | ✅ |
| „Welt lädt noch"-Unterscheidung | ✅ | ✅ | ✅ |
| Spielerzahl live | ✅ | ✅ | ✅ |
| Laufzeit-Anzeige | ✅ | ✅ | ✅ |
| Schutz vor Stop bei Spielern | ✅ | ✅ | ⏳ |
| Variable Farben (Hintergrund/Akzent) | — | ✅ | ✅ |
| Line-Art-Icons, keine Emoji | — | ✅ | ✅ |
| Backup anlegen / listen / löschen | ✅ | ✅ | ⏳ |
| Restore (Boot-Disk-Tausch) | ✅ | ✅ | ⏳ |
| Kostenübersicht | ✅ | ✅ | ✅ |
| Aufteilung pro Spieler | ✅ | ✅ | ✅ |
| Sessions im Zeitraum | ✅ | ✅ | ⏳ |
| Zeitraum umschaltbar (Tag…Jahr) | ✅ | ✅ | ⏳ |
| Spieler umbenennen (Stift) | — | ✅ | ⏳ |
| Self-Update über GitHub Releases | — | ✅ | ❌ Signatur, siehe STATE A1 |

## Wie die App sich selbst aktualisiert

Das Repo ist öffentlich, also braucht der Update-Weg keine Credentials:

1. Die GitHub-Action baut bei jedem Push die APK und veröffentlicht sie als
   Release mit dem Tag `app-v0.2.0-<run_number>`.
2. `versionCode` ist dieselbe `run_number`, so sind zwei Builds immer
   eindeutig sortierbar.
3. Die App liest `releases/latest`, vergleicht die Build-Nummer aus dem Tag
   mit ihrer eigenen und bietet ein Update an, wenn sie größer ist.
4. Beim Antippen lädt sie die APK in ihren Cache und übergibt sie dem
   System-Installer (`REQUEST_INSTALL_PACKAGES` + FileProvider).

Du musst also nur einmal manuell installieren; danach meldet sich die App
selbst, sobald ich etwas gepusht habe.

## Offene Punkte

### B1 — Wie kommt die Function an die Dateien auf der VM?
Weltwechsel und die SotF-Config arbeiten auf
`/home/kradtke79/sotf/userdata`. Die Function kann dort nicht direkt hin.
Optionen:
- **(a)** Startup-Script-Metadata setzen und VM neu starten — funktioniert,
  aber grob und nur beim Boot.
- **(b)** Kleiner HTTP-Dienst auf der VM, den die Function aufruft. Braucht
  eine Firewall-Regel; Cloud Functions haben keine feste Egress-IP, außer
  mit VPC-Connector + Cloud NAT.
- **(c)** Ein Skript auf der VM, das per Cron eine „Aufträge"-Datei in einem
  GCS-Bucket abholt. Function schreibt rein, VM arbeitet ab. Simpel, aber
  mit Verzögerung.

Für **Backups** hat sich die Frage erledigt: Disk-Snapshots über die
Compute API brauchen nichts auf der VM und laufen auch, wenn sie aus ist.
Der Preis ist Granularität — gesichert wird die ganze Platte, nicht nur
`userdata`.

### B2 — Kosten pro Spieler — ✅ gelöst
Nicht über Stichproben, sondern exakt: der Ops Agent schickt das
Container-Log an Cloud Logging, und dort steht bei jedem Join und Leave
die Steam-ID mitsamt Anzeigename. Keine Interpolation, keine Lücken
zwischen Messpunkten. Die Steam-Query kam dafür nicht in Frage — SotF
beantwortet A2S_PLAYER mit leeren Namensfeldern.

### B3 — Konsolen-Befehle (Item geben, Teleport)
Der SotF-Dedicated-Server hat kein RCON. Es gibt keinen dokumentierten Weg,
ihm von außen Befehle zu schicken. Realistisch:
- App zeigt den fertigen Befehl mit Copy-Button, Eingabe im Spiel per F1.
- Oder RedLoader-Mod auf dem Server — anderes Image, mehr Wartung.

**→ Mit Kevke klären, ob das den Aufwand wert ist.**

## Design-Regeln (verbindlich)

- **Nur Line-Art-Icons**, keine Emoji, keine gefüllten Flächen.
  Stroke-Width 1.6, runde Enden, alle selbst als Vektorpfade definiert.
- **Variable Hintergrund- und Akzentfarbe.** Fünf Hintergründe (Tinte,
  Kohle, Rinde, Papier, Leinen), sechs Akzente (Salbei, Ton, Schiefer,
  Rost, Moos, Sand). Bewusst gedämpft — kein Indigo/Violett/Cyan.
- **Schlicht.** Keine Gradients, kein Glassmorphism, keine Glows.
- Typografie: System-Sans, ein Schnitt, Hierarchie über Größe.
- Ecken: 12 dp durchgehend.

## Changelog

- **2026-08-23** — Spieleridentität aus dem Container-Log statt aus der
  Steam-Query, Kostenaufteilung nach Kevkes Zerlegung, Sessions und
  umschaltbare Zeiträume. Fester Signaturschlüssel für die CI, weil
  jeder Build bis dahin einen eigenen hatte und der Selbst-Updater
  deshalb nie installieren konnte. `deploy.sh` lehnt Platzhalter-Token
  ab. Gesamtstand in `STATE.md` festgehalten.
- **2026-08-22** — Backups (Disk-Snapshots), Kostenübersicht und
  Self-Update über GitHub Releases. Function-Timeout auf 540 s wegen des
  Boot-Disk-Tauschs beim Restore; `roles/logging.viewer` dazu, weil die
  Kostenrechnung Start/Stop aus dem Log liest — der Idle-Shutdown
  terminiert von innen und taucht nie als Compute-API-Stop auf.
- **2026-08-21 (2)** — Kurs korrigiert: Firestore, Job-Queue und VM-Agent
  wieder rausgeworfen. Alles läuft über eine Function mit Actions in der
  URL, so wie von Anfang an gewünscht. App auf zwei Felder eingedampft
  (URL + Token). Steam-Query in die Function verlegt.
- **2026-08-21 (1)** — Erste Fassung mit Backend, Agent und App-Gerüst.
