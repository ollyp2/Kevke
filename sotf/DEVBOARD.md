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

Ohne `format=json` bzw. `Accept: application/json` kommt eine lesbare
Zeile zurück — genau wie beim alten `sotf-start-trigger`, damit die Links
im Browser und im Chat brauchbar bleiben.

## Komponenten & Status

| # | Komponente | Pfad | Status |
|---|---|---|---|
| 1 | Control Function | `sotf/function/` | ✅ Code fertig, ⏳ nicht deployed |
| 2 | Android-App | `sotf/android/` | ✅ Code fertig, ⏳ nicht gebaut |
| 3 | DEVBOARD | `sotf/DEVBOARD.md` | ✅ |
| 4 | Handoff-Doku | `sotf/HANDOFF.md` | ✅ |

## Feature-Matrix

| Feature | Function | App-UI | Verifiziert |
|---|---|---|---|
| An/Aus (großer runder Knopf) | ✅ | ✅ | ⏳ |
| Status-Lampe | ✅ | ✅ | ⏳ |
| „Welt lädt noch"-Unterscheidung | ✅ | ✅ | ⏳ |
| Spielerzahl live | ✅ | ✅ | ⏳ |
| Laufzeit-Anzeige | ✅ | ✅ | ⏳ |
| Schutz vor Stop bei Spielern | ✅ | ✅ | ⏳ |
| Variable Farben (Hintergrund/Akzent) | — | ✅ | ⏳ |
| Line-Art-Icons, keine Emoji | — | ✅ | ⏳ |

## Geplant — jeweils als neue Action

Das Muster bleibt gleich: eine Action dazu, ein Screen dazu.

| Feature | Action | Braucht |
|---|---|---|
| Backup jetzt | `action=backup` | Zugriff auf `userdata/` → siehe B1 |
| Backup-Liste | `action=backups` | dito |
| Restore | `action=restore&name=…` | dito |
| Config lesen/schreiben | `action=config` | dito |
| Weltwechsel | `action=world&slot=N` | dito |
| Kostenübersicht | `action=billing` | Laufzeit-Historie → siehe B2 |
| Item geben / Teleport | `action=give` … | Konsolen-Kanal → siehe B3 |

## Offene Punkte

### B1 — Wie kommt die Function an die Dateien auf der VM?
Backups, Config und Weltwechsel arbeiten auf `/home/kradtke79/sotf/userdata`.
Die Function kann dort nicht direkt hin. Optionen:
- **(a)** Startup-Script-Metadata setzen und VM neu starten — funktioniert,
  aber grob und nur beim Boot.
- **(b)** Kleiner HTTP-Dienst auf der VM, den die Function aufruft. Braucht
  eine Firewall-Regel; Cloud Functions haben keine feste Egress-IP, außer
  mit VPC-Connector + Cloud NAT.
- **(c)** Ein Skript auf der VM, das per Cron eine „Aufträge"-Datei in einem
  GCS-Bucket abholt. Function schreibt rein, VM arbeitet ab. Simpel, aber
  mit Verzögerung.
- **(d)** Backups komplett ohne VM: Persistent-Disk-Snapshots über die
  Compute API. Die Function kann das allein, ohne irgendetwas auf der VM.
  Sichert die ganze Platte statt nur `userdata`.

**→ (d) ist der klar einfachste Weg für Backups** und passt zum Leitprinzip.
Für Config/Weltwechsel bräuchte es trotzdem einen der anderen Wege.

### B2 — Laufzeit-Historie für die Kostenübersicht
Die Compute API liefert nur `lastStartTimestamp` / `lastStopTimestamp`,
keine Historie. Für „was hat der Monat gekostet" braucht es entweder:
- Cloud Logging abfragen (Start/Stop-Events stehen dort ohnehin drin), oder
- die Function schreibt bei jedem Start/Stop eine Zeile weg.

**→ Cloud Logging ist der Weg ohne zusätzlichen Speicher.**

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

- **2026-08-21 (2)** — Kurs korrigiert: Firestore, Job-Queue und VM-Agent
  wieder rausgeworfen. Alles läuft über eine Function mit Actions in der
  URL, so wie von Anfang an gewünscht. App auf zwei Felder eingedampft
  (URL + Token). Steam-Query in die Function verlegt.
- **2026-08-21 (1)** — Erste Fassung mit Backend, Agent und App-Gerüst.
