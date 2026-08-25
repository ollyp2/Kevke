# STATE — vollständiger Stand des Repos

Stand: 2026-08-23. Branch `claude/sotf-server-setup-llure1`.

Dieses Dokument beschreibt, **was existiert, was läuft und was noch offen
ist** — vollständig genug, dass jemand ohne Chatverlauf weiterarbeiten
kann. `DEVBOARD.md` hält Architektur und Design-Regeln, `README.md` die
Server-Bedienung, `HANDOFF.md` die Deploy-Schritte. Hier steht der
Gesamtzusammenhang und die Liste der offenen Punkte.

---

## 1. Was im Repo liegt

Das Repository `ollyp2/Kevke` ist **öffentlich** und enthält zwei
voneinander unabhängige Projekte:

### 1.1 KevWorkdesk (Repo-Wurzel) — nicht Teil dieses Projekts

```
index.html, KevWorkdesk_Complete.html
css/styles.css
js/  (main, ui, storage, config, buttons, notes, offers, files,
      calculator, averageCalculator, dateCalculator,
      timer-core, timer-ui, timer-notifications)
```

Eine reine Frontend-Anwendung, älterer Stand, wird von der SotF-Arbeit
nicht angefasst. Steht nur hier, damit niemand sie versehentlich für
Server-Code hält.

### 1.2 SotF-Serversteuerung (`sotf/`)

```
sotf/
├── STATE.md                     ← dieses Dokument
├── DEVBOARD.md                  Architektur, Design-Regeln, Changelog
├── README.md                    Server von Grund auf aufsetzen + Betrieb
├── HANDOFF.md                   Deploy-Schritte für den Menschen
│
├── docker-compose.yml           Game-Container, UDP-Ports, .env-getrieben
├── .env.example                 SERVERNAME / SERVERPASSWORD
├── setup-sotf.sh                idempotenter VM-Bootstrap (als root)
│
├── idle-shutdown/
│   ├── sotf-idle-shutdown.sh    Wächter: CPU der Container prüfen
│   └── sotf-idle-shutdown.service
│
├── gcp/
│   ├── firewall.sh              UDP-Regel korrigieren
│   └── static-ip.sh             ephemere IP zur statischen machen
│
├── agent/
│   └── install-ops-agent.sh     Ops Agent → Container-Log nach Cloud Logging
│
├── function/                    ← das Herzstück
│   ├── main.py                  alle Actions, ~760 Zeilen
│   ├── deploy.sh                Deploy + IAM + Token-Prüfung
│   └── requirements.txt
│
└── android/                     Kotlin + Jetpack Compose
    ├── settings.gradle.kts, build.gradle.kts, gradle.properties
    └── app/
        ├── build.gradle.kts     versionCode = CI-Run-Nummer, Signatur
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/values/{strings,themes}.xml
            ├── res/xml/{file_paths,data_extraction_rules}.xml
            └── java/de/kevke/servercontrol/
                ├── MainActivity.kt        Navigation, Drawer
                ├── AppViewModel.kt        aller Zustand, Polling
                ├── data/ControlConfig.kt  label / baseUrl / token
                ├── data/Settings.kt       EncryptedSharedPreferences
                ├── net/ControlClient.kt   Ktor, Datenmodelle
                ├── net/UpdateChecker.kt   Selbst-Update via GitHub
                ├── ui/components/Common.kt
                ├── ui/theme/Theme.kt      5 Hintergründe, 6 Akzente
                ├── ui/theme/Icons.kt      alle Icons als Vektorpfade
                └── ui/screens/
                    ├── HomeScreen.kt      runder An/Aus-Knopf, Lampe
                    ├── BackupsScreen.kt   Snapshots
                    ├── BillingScreen.kt   Kosten, Spieler, Sessions
                    └── SetupScreen.kt     URL/Token, Farben, Update
```

Dazu `.github/workflows/build-android.yml` in der Repo-Wurzel: baut die
APK und veröffentlicht sie als GitHub Release.

---

## 2. Was tatsächlich läuft

| Sache | Wert |
|---|---|
| GCP-Projekt | `gen-lang-client-0146272192` (Nummer `626518798042`) |
| VM | `sotf-server`, Zone `europe-west3-a` |
| Region der Function | `europe-west3` |
| Function | `sotf-control`, Gen 2, `python312`, Entry `sotf_control` |
| Function-Basis-URL | `https://sotf-control-4hfkfd54na-ey.a.run.app` |
| Service-Account | `626518798042-compute@developer.gserviceaccount.com` |
| Game-Image | `jammsen/sons-of-the-forest-dedicated-server:latest` |
| Savegames | `/home/kradtke79/sotf/userdata` auf der VM |
| Stundensatz | `HOURLY_RATE=0.17` EUR (Env-Var der Function) |
| App-Paket | `de.kevke.servercontrol` |

**Rollen des Service-Accounts** (setzt `deploy.sh` bei jedem Lauf):

- `roles/compute.instanceAdmin.v1` — Start/Stop, Snapshots, Disk-Tausch
- `roles/monitoring.viewer` — gemessene Laufzeit lesen
- `roles/logging.viewer` — Container-Log für die Spieleraufteilung lesen
- `roles/logging.logWriter` — damit der Agent auf der VM überhaupt schreiben darf

**Firewall** `sotf-game-ports`, ausschließlich UDP:

| Port | Zweck |
|---|---|
| 8766 | Spiel |
| 27016 | Steam-Query |
| 9700 | BlobSync (Weltsynchronisation) |

SotF benutzt für das Spiel **kein TCP**. Ein früher vorhandenes
`tcp:9700` in der Regel war schlicht falsch.

---

## 3. Wie die Teile zusammenhängen

```
┌──────────────┐   GET ?token=…&action=…   ┌────────────────────┐
│ Android-App  │ ────────────────────────► │  sotf-control      │
│  (S26 Ultra) │ ◄──────────────────────── │  (Cloud Function)  │
└──────────────┘        JSON / Text        └─────────┬──────────┘
                                                     │
              ┌──────────────┬───────────────────────┼──────────────┐
              ▼              ▼                       ▼              ▼
      ┌──────────────┐ ┌───────────┐        ┌──────────────┐ ┌───────────┐
      │ Compute API  │ │ Monitoring│        │ Cloud Logging│ │ UDP 27016 │
      │ start/stop   │ │ Laufzeit  │        │ wer war on   │ │ joinbar?  │
      │ Snapshots    │ │ gemessen  │        │ Steam-IDs    │ │ Spielerzahl│
      └──────────────┘ └───────────┘        └──────────────┘ └───────────┘
```

**Leitprinzip:** alles über die eine Function, Actions als
Query-Parameter. Jede Aktion der App ist ein Link, den man genauso in
den Browser tippen könnte. Kein Firestore, keine Job-Queue, kein
eigener Dienst auf der VM.

### Alle Actions

| URL-Zusatz | Wirkung |
|---|---|
| `&action=start` | VM starten |
| `&action=stop` | stoppen, lehnt ab solange Spieler drauf sind |
| `&action=stop&force=1` | trotzdem stoppen |
| `&action=status` | Zustand, IP, Spielerzahl, Laufzeit, joinbar? |
| `&action=backup&label=…` | Disk-Snapshot anlegen |
| `&action=backups` | Snapshots auflisten |
| `&action=restore&name=…` | Boot-Disk auf einen Snapshot zurücksetzen |
| `&action=delete_backup&name=…` | Snapshot löschen |
| `&action=billing&range=day\|week\|month\|quarter\|year` | Kosten und Sessions |

Ohne `&format=json` bzw. `Accept: application/json` kommt eine lesbare
deutsche Zeile zurück, damit die Links im Browser brauchbar bleiben.

### Der Weg von „wer hat gespielt" zur Rechnung

Vier Bausteine, jeder mit einem Grund:

1. **Ops Agent auf der VM** liest
   `/var/lib/docker/containers/*/*-json.log` und schickt jede Zeile an
   Cloud Logging.
2. **Die Function filtert** auf zwei Muster im Log:
   - `Steam auth successful for client N with steam id <17 Ziffern>, username <Name>` → Join
   - `Unregistering client N with steam id <17 Ziffern>` → Leave
3. **Cloud Monitoring** liefert die tatsächlich gemessene Laufzeit
   (`compute.googleapis.com/instance/uptime`, DELTA-Metrik, `ALIGN_SUM`).
4. **Die Aufteilung** läuft die Zeitachse ab und belastet jede Strecke
   nach der Anzahl der Anwesenden.

Kevkes Zerlegung, die dahintersteckt:

```
S = A + B + C + … + N
A = M/L·G      Zeit allein         → voller Satz
B = M/L·G/2    zu zweit            → halber Satz
C = M/L·G/3    zu dritt            → ein Drittel
```

`G/L` ist der Stundensatz, also kostet eine Sekunde mit *n* Leuten
drauf jeden von ihnen `satz/n`. Die Summe aller Anteile ergibt exakt die
Kosten der belegten Zeit; die leere Zeit (Hochfahren, Leerlauf bis zum
Auto-Aus) wird **nicht** auf die Spieler verteilt, sondern getrennt als
„Niemand drauf" ausgewiesen.

### Sessions

Die Laufzeit kommt ohnehin schon in Zeitfenstern (5 min bei Tagesansicht,
bis 6 h bei Jahresansicht). Zusammenhängende Fenster mit Laufzeit größer
null sind eine Session. Ein einzelnes leeres Fenster wird toleriert,
damit ein kurzer Aussetzer beim Agent nicht einen Spieleabend in zwei
Sessions zerschneidet.

### Bekannte Spieler

| Steam-ID | Name im Log |
|---|---|
| 76561198104125441 | Kevke Maradtinio |
| 76561198179125691 | Frägger |
| 76561198395617487 | Der_Tischler |
| 76561198682003138 | Nice_Guy |

Die App kann jeden davon per Stift umbenennen. Die Zuordnung liegt in
den verschlüsselten App-Einstellungen, nicht in der Function — deshalb
ist sie nach einer Neuinstallation weg.

---

## 4. Teuer gelernt — nicht nochmal hineinlaufen

Diese Punkte haben jeweils eine ganze Runde gekostet. Sie stehen hier,
damit sie das nicht nochmal tun.

**Der Idle-Shutdown darf nicht am Log hängen.** Die erste Fassung suchte
`Set target framerate: 5` als letzte Zeile. Die Zeile kommt nicht
zuverlässig — einmal lief die VM 11 Stunden leer weiter. Jetzt wird die
CPU des Containers über `docker stats` gemessen.

**Der Schwellwert ist 80 %, nicht 10 %.** Gemessen: leer 40–43 %, mit
Spielern über 150 %. Der Vorgabewert 10 % im Skript hätte nie ausgelöst.

**Winkelklammern in kopierbaren Befehlen sind gefährlich.** Ein
`mv … userdata.bak.<zeitstempel>` wurde wörtlich eingefügt; die Shell
las `<` als Eingabeumleitung, das `rm -rf userdata` davor lief, das `mv`
scheiterte. Savegames waren weg und mussten aus dem Backup zurück.
**Nie** `<platzhalter>` in eine Zeile schreiben, die jemand ausführen
soll — stattdessen eine Variable, die er vorher setzt.

**Platzhalter-Werte generell.** `export TOKEN=dein_token` wurde
wörtlich übernommen und landete live in der Function. `deploy.sh` lehnt
solche Werte inzwischen ab (leer, unter 20 Zeichen, oder ein
offensichtliches Füllwort).

**Der Idle-Shutdown erzeugt kein Compute-API-Stop-Event.** Er fährt die
VM von innen herunter. Wer Start/Stop aus dem Audit-Log paart, bekommt
Unsinn heraus — einmal 679 Stunden für einen Monat. Laufzeit kommt
deshalb aus Cloud Monitoring, gemessen statt rekonstruiert.

**Zeitzonen, zweimal.** `lastStartTimestamp` kommt mit Offset; wer die
ersten 19 Zeichen abschneidet und als UTC liest, verschiebt die Laufzeit
um den Offset. `datetime.fromisoformat` auf den vollen String benutzen.

**Docker schreibt Nanosekunden.** `datetime.fromisoformat` verträgt nur
Mikrosekunden. Die Bruchstelle muss vorher gekürzt werden.

**Der Zeitstempel des Log-Eintrags ist nicht der Zeitstempel des
Ereignisses.** Als der Agent das bestehende 1,6-MB-Log das erste Mal
las, stempelte Cloud Logging eine ganze Nacht auf einen Augenblick —
alle Spielzeiten kamen als null heraus. Es zählt Dockers eigenes
`time`-Feld im JSON-Payload, nicht der Ingest-Zeitpunkt.

**A2S_PLAYER liefert keine Namen.** Der SotF-Server antwortet auf die
Steam-Abfrage mit einem leeren Namensfeld pro Slot (`… 50 c4 32 42` mit
einem einzelnen Nullbyte). Die Abfrage taugt für die *Anzahl*, nie für
die *Identität*. Deshalb der Umweg über das Container-Log.

**`sudo ls /var/lib/docker/containers/*/*-json.log` schlägt fehl.** Die
Shell löst das Sternchen als unprivilegierter Nutzer auf, bevor `sudo`
läuft. `sudo sh -c "ls …"` benutzen.

**Die Compute API akzeptiert `filter` und `orderBy` bei Snapshots nicht
gleichzeitig** („Specifying both a list filter and sort order is not
currently supported"). In Python sortieren.

**gcloud stürzt beim Redeploy einer Gen-2-Function ab**
(`AttributeError: 'NoneType' object has no attribute 'dockerRepository'`),
wenn `--docker-repository` nicht ausgeschrieben ist.

**docker-compose v1 bricht beim Neuerstellen** mit
`KeyError: 'ContainerConfig'`. Erst `down`, dann `up -d`. Unter v2 nicht
nötig.

**Die SotF-Config benutzt Punktnamen.** Nicht `StructureDamage`, sondern
`GameSettings.Structure.Damage`; ebenso `GameSetting.Vail.EnemyHealth`
und `GameSetting.Survival.SingleUseContainers`. Das wurde mehrfach
geraten statt nachgeschlagen — Kevke hat zu Recht gefragt, warum keine
Dokumentation gesucht wird. **Erst suchen, dann schreiben.**

**„Bauanimationen überspringen" gibt es auf dem Dedicated Server
nicht.** Es existiert kein solcher Schlüssel.

**Die Welt führt ihre eigene Kopie der Regeln, und die gewinnt.** Das war
die Ursache hinter wochenlangem Rätselraten. In `dedicatedserver.cfg`
gibt es zwei Blöcke, die sich völlig verschieden verhalten:

- `GameSettings` greift auf jeder Welt, auch auf einer laufenden.
  `Structure.Damage: false` hat funktioniert — als einziges.
- `CustomGameModeSettings` wird gelesen und dann ignoriert, weil die
  Welt ihre Regeln aus dem Spielstand zieht.

Diese Kopie liegt in `GameSetupSaveData.json` innerhalb von
`SaveData.zip`, als JSON in einen String kodiert, der wiederum in JSON
steht:

```
{"Version":"0.0.0","Data":{"GameSetup":"{\"_settings\":[
  {\"Name\":\"Mode\",\"SettingType\":3,\"StringValue\":\"Hard\"}, …]}"}}
```

Dort stand `Mode: "Hard"` — deshalb konnte die Config sagen was sie
wollte. `sotf/config/apply-world-settings.py` schreibt diese Liste um;
danach funktionierten Container-Respawns sofort.

Nebenbei erklärt dieselbe Liste, warum Wild knapp war: der Hard-Modus
hatte `AnimalSpawnRate` auf `LOW` gesetzt.

Wertformat: Booleans tragen `BoolValue` und kein `SettingType`, Strings
tragen `SettingType: 3` und `StringValue`. Beides von der Welt
abgeschaut, nicht erfunden. `UID` niemals anfassen.

**Jeder CI-Build hatte bis Build 8 einen anderen Signaturschlüssel.**
`assembleDebug` signiert mit `~/.android/debug.keystore`, und der Runner
erzeugt den bei jedem Lauf neu. Nachgewiesen an den veröffentlichten
APKs:

```
Build 6   a5bb971a8a696163bf8b16f569400d6a51cb6ee4b500506542a513a2176f72a2
Build 8   74f0ca6985a8c90e2db277ab8b53b09858198590e77e6ff5964520960ff64ab5
```

Android installiert ein Update nur bei **gleichem** Schlüssel. Der
Selbst-Updater konnte also herunterladen, aber nie installieren. Behoben
über das Secret `KEYSTORE_BASE64`; siehe To-do A1.

**Nicht über Kevke spekulieren.** Zweimal falsch geraten: welcher
Spielstand welchem Spieler gehört (er war der zweite Spieler, nicht der
Host), und dass er keine statische IP habe. Fragen kostet eine Zeile,
Raten kostet eine Runde.

---

## 5. Was verifiziert ist und was nicht

| Sache | Stand |
|---|---|
| Start / Stop / Status aus der App | ✅ im Betrieb bestätigt |
| Statuslampe inkl. „Welt lädt noch" | ✅ |
| Spielerzahl live | ✅ |
| Laufzeit korrekt (nach Zeitzonenfix) | ✅ |
| Idle-Shutdown greift wirklich | ✅ mit 80 % Schwellwert |
| Firewall nur UDP | ✅ |
| Statische IP | ✅ reserviert |
| Kosten gesamt, aus Monitoring | ✅ |
| Container-Respawns über den Spielstand | ✅ im Spiel bestätigt |
| Aufteilung pro Spieler | ✅ nachgerechnet: Tagesansicht Kevke 4111 s allein = 0,19 € plus 0,17 € nicht zugeordnet = 0,37 € gesamt |
| Sessions | ⏳ Code deployed, in echten Daten noch nicht angesehen |
| Backup anlegen / listen | ⏳ |
| Restore (Boot-Disk-Tausch) | ⏳ nie an echten Daten ausprobiert |
| Selbst-Update der App | ❌ scheitert bis Build 8 an der Signatur |

---

## 6. Offene To-dos

### A — blockiert die Nutzung

**A1. Signaturschlüssel einrichten.** Ohne ihn ist jede neue Version
eine Handinstallation.

1. In der Cloud Shell:
   ```
   cd ~
   export KSPASS=$(openssl rand -hex 16)
   keytool -genkeypair -v -keystore sotf-signing.jks -alias sotf \
     -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12 \
     -dname "CN=Server Control, O=Kevke, C=DE" \
     -storepass "$KSPASS" -keypass "$KSPASS"
   base64 -w0 sotf-signing.jks > sotf-signing.b64
   echo "$KSPASS"
   ```
2. Vier Secrets unter *Settings → Secrets and variables → Actions*:
   `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS=sotf`.
3. `sotf-signing.jks` aus der Cloud Shell herunterladen und sichern.
   **Ist die Datei weg, ist jedes künftige Update wieder eine
   Handinstallation.**
4. Danach einmal deinstallieren und den ersten signierten Build
   installieren. Das ist die letzte Handinstallation.

**A2. Sessions an echten Daten prüfen.** `&action=billing&range=week`
aufrufen und schauen, ob die Zeitspannen zu tatsächlichen Spieleabenden
passen. Wenn nicht, ist `find_sessions` in `main.py` die Stellschraube
(Fenstergröße über `ALIGNMENT`, Toleranz über `gap_tolerance`).

### B — Aufräumen und Absicherung

**B1. Alte Function löschen.**
```
gcloud functions delete sotf-start-trigger \
  --region=europe-west3 --project=gen-lang-client-0146272192
```

**B2. Alten Token aus `HANDOFF.md` entfernen.** ✅ erledigt am
2026-08-23. Der Klartext-Token ist aus der Datei raus. **Er steht aber
weiterhin in der Git-Historie** — das lässt sich nur durch Umschreiben
der Historie beseitigen, was bei einem öffentlichen Repo mehr kaputt
macht als es nützt. Der Token ist seitdem zweimal ersetzt worden und
damit tot; die Lehre ist, dass ein Geheimnis, das einmal committet
wurde, als verbrannt gilt.

**B3. Google-Konto absichern.** Das Passwort zum Konto `kradtke79@gmail.com`
wurde einmal in einen Chat eingefügt. Es sollte gewechselt und
Zwei-Faktor aktiviert werden, unabhängig davon, ob etwas passiert ist.

**B4. Ops-Agent-Konfiguration angleichen.** Die Datei auf der VM wurde
direkt über SSH geschrieben und hat nur den Prozessor `docker_json`. Das
Skript im Repo (`agent/install-ops-agent.sh`) definiert zusätzlich
`lift_log_line`. Beides funktioniert, weil die Function sowohl
`message` als auch `log` liest — aber Repo und Wirklichkeit sollten
übereinstimmen, sonst richtet ein späteres Ausführen des Skripts etwas
anderes ein als das, was heute läuft.

**B5. Backup und Restore einmal wirklich testen.** Der Restore tauscht
die Boot-Disk. Er hebt die alte Disk auf statt sie zu löschen, ist also
umkehrbar — aber er war noch nie im Ernstfall dran. Am besten testen,
solange nichts Wichtiges davon abhängt.

### C — Funktionslücken

**C1. Wie kommt die Function an die Dateien auf der VM?** Weltwechsel
und die SotF-Config liegen unter `/home/kradtke79/sotf/userdata`. Die
Function kann dort nicht hin. Optionen:
- Startup-Script-Metadata setzen und neu starten — funktioniert, aber
  grob und nur beim Boot.
- Kleiner HTTP-Dienst auf der VM. Braucht eine Firewall-Regel, und
  Cloud Functions haben keine feste Egress-IP (nur mit VPC-Connector
  und Cloud NAT).
- Auftragsdatei in einem GCS-Bucket, die ein Cronjob auf der VM abholt.
  Simpel, aber mit Verzögerung.

Für Backups hat sich die Frage erledigt — Disk-Snapshots über die
Compute API brauchen nichts auf der VM.

**C2. Konsolenbefehle (Item geben, Teleport).** Der SotF-Dedicated-Server
hat kein RCON, es gibt keinen dokumentierten Weg von außen. Realistisch
entweder Befehl anzeigen mit Kopierknopf und im Spiel per F1 eingeben,
oder RedLoader-Mod — anderes Image, mehr Wartung. **Mit Kevke klären, ob
das den Aufwand wert ist.**

**C3. `CustomGameModeSettings` greifen nicht.** ✅ gelöst am 2026-08-25,
siehe Abschnitt 4. Die Regeln stehen im Spielstand, nicht in der Config.
`apply-world-settings.py` schreibt sie dorthin; Container-Respawns
funktionieren seitdem.

**C5. Hält die Änderung einen Neustart aus?** Nach dem ersten Serverlauf
stand `Mode` wieder auf `Hard`, obwohl die Regeln wirken. Der Server hat
die Datei also angefasst. Zu prüfen, ob die hinzugefügten
`GameSetting.…`-Einträge erhalten bleiben. Verschwinden sie, muss die
Function sie nach jedem Start neu setzen.

**C6. Container tot heißt VM läuft ewig.** Der Idle-Shutdown setzt seinen
Zähler zurück, wenn der Container nicht läuft (`cpu == -1`). Beim
bewussten Stoppen ist das richtig — stirbt der Container aber dauerhaft,
läuft die VM auf Kosten weiter, ohne dass jemand spielen kann.

**C4. Spielerdaten reichen nur bis zum Agent-Start zurück.** Vor dem
Zeitpunkt, an dem der Ops Agent zu senden begann, gibt es keine
Join/Leave-Zeilen. Ältere Zeiträume zeigen Laufzeit und Gesamtkosten,
aber keine Aufteilung. Das ist keine Fehlfunktion, sondern fehlende
Vergangenheit.

---

## 7. Verbindliche Regeln der Zusammenarbeit

Diese stehen hier, weil sie mehrfach eingefordert wurden.

**Vor jeder Aktion fragen, die Daten löschen oder den Server offline
nehmen könnte** — kurz begründen, warum.

**Savegames unter `/home/kradtke79/sotf/userdata` niemals löschen.**

**Zeigen, was ausgeführt wird**, statt es zusammenzufassen. Kevke will
es lernen, nicht abnicken.

**Der Rhythmus:** ich gebe Befehle, er führt aus und gibt die Ausgabe
zurück, ich prüfe *bevor* etwas geändert wird. Wenn ein einfacherer Weg
sichtbar wird, umstellen; wenn eine Änderung es verkompliziert, sein
lassen.

**Alles über die Function mit Trigger-URLs.** Der Umweg über Firestore,
Job-Queue und VM-Agent wurde ausdrücklich verworfen und wieder entfernt.

**Kosten werden abgefragt, nicht geschätzt.**

**Design der App:** nur Line-Art-Icons, keine Emoji, keine gefüllten
Flächen. Variable Hintergrund- und Akzentfarbe, bewusst gedämpft —
keine typischen KI-Farben, kein Indigo, Violett, Cyan. Keine Gradients,
kein Glassmorphism, keine Glows. Ecken durchgehend 12 dp.

**Keine Zugangsdaten** in Dateien, Commits oder Anfragen an Dritte —
auch dann nicht, wenn sie im Chat auftauchen.
