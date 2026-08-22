# HANDOFF — was du machen musst

Zwei Schritte: Function deployen, APK bauen. Zusammen etwa 15 Minuten.

---

## 1. Function deployen

In **Cloud Shell**:

```bash
cd ~
rm -rf Kevke-sotf
git clone -b claude/sotf-server-setup-llure1 \
  https://github.com/ollyp2/Kevke.git Kevke-sotf

cd Kevke-sotf/sotf/function
export TOKEN=$(openssl rand -hex 24)
echo "TOKEN: $TOKEN"      # notieren!
bash deploy.sh
```

Am Ende gibt das Skript deine drei Links aus. So etwas wie:

```
Start:  https://sotf-control-xxxx.a.run.app?token=…&action=start
Stop:   https://sotf-control-xxxx.a.run.app?token=…&action=stop
Status: https://sotf-control-xxxx.a.run.app?token=…&action=status
```

**Berechtigung setzen** — das Skript zeigt den fertigen Befehl an, etwa:

```bash
gcloud projects add-iam-policy-binding gen-lang-client-0146272192 \
  --member="serviceAccount:<PROJEKTNUMMER>-compute@developer.gserviceaccount.com" \
  --role="roles/compute.instanceAdmin.v1"
```

**Prüfen** — den Status-Link in den Browser:
```
Server laeuft. 0 Spieler.
```
oder
```
Ist schon aus.
```

Wenn das kommt, ist alles fertig. Der Start-Link ersetzt ab jetzt deinen
alten `sotf-start-trigger`.

---

## 2. APK bauen

Kein Android Studio nötig.

1. Auf GitHub: **Actions** → **Build Server Control APK** → **Run workflow**
   → Branch `claude/sotf-server-setup-llure1` → Start
2. Nach ~5 Minuten unter **Artifacts**: `server-control-debug.zip`
3. Herunterladen, entpacken, `.apk` aufs S26 Ultra
4. Installieren — Android fragt einmal nach „Unbekannte Quellen"

---

## 3. App verbinden

Beim ersten Start:

| Feld | Wert |
|---|---|
| Name | `SotF` (frei wählbar) |
| Function-URL | die Basis-URL, **ohne** `?token=…` |
| Token | der Wert aus Schritt 1 |

Also z. B. URL `https://sotf-control-xxxx.a.run.app` und Token separat.
Die App hängt `?token=…&action=…` selbst an.

Sie prüft die Eingaben sofort — falsche URL oder falscher Token werden
direkt gemeldet, nichts wird blind gespeichert.

---

## Was die App danach kann

- **Großer runder Knopf** — an und aus
- **Status-Lampe** — Aus / Startet / Online, wobei „Online" erst kommt
  wenn die Welt wirklich geladen ist und man joinen kann
- **Spielerzahl** live vom Server
- **Laufzeit**, damit du die Kosten im Blick hast
- **Stop-Schutz** — wenn Spieler drauf sind, fragt sie nach
- **Farben** unter Einstellungen umschaltbar

---

## Wichtig: alten Token wechseln

`420LangeHaareUhh` steht inzwischen in unserem Chatverlauf. Der neue
Token aus Schritt 1 ersetzt ihn. Räum die alte Function danach weg:

```bash
gcloud functions delete sotf-start-trigger \
  --region=europe-west3 --project=gen-lang-client-0146272192
```

---

## Wenn etwas nicht klappt

**`noe_` im Browser** → Token stimmt nicht.

**„Fehler: …" mit Permission-Text** → IAM-Rolle aus Schritt 1 fehlt oder
ist noch nicht durch (kann zwei Minuten dauern).

**Status sagt `RUNNING`, aber `gameReady: false` bleibt** → die VM läuft,
der Container antwortet nicht auf Port 27016. Prüfen:
```bash
gcloud compute firewall-rules describe sotf-game-ports \
  --format='value(allowed)' --project=gen-lang-client-0146272192
```
Muss `udp:8766,27016,9700` enthalten.

**App sagt „Keine Antwort"** → URL falsch (Tippfehler, fehlendes `https://`,
oder das `?token=…` versehentlich mit reinkopiert).

**APK-Build rot** → schick mir den Log-Ausschnitt aus der Action.

---

## Was als nächstes dazukommen kann

Jedes weitere Feature ist eine Action mehr in derselben Function — siehe
DEVBOARD. Am einfachsten wären:

- **Backups** über Disk-Snapshots (Function kann das allein, ohne
  irgendetwas auf der VM)
- **Kostenübersicht** aus Cloud Logging (Start/Stop-Events stehen da schon)

Sag Bescheid was du zuerst willst.
