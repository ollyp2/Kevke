# HANDOFF — was du machen musst

Alles was ich alleine bauen konnte, ist gebaut. Hier stehen die Schritte,
für die deine Credentials, deine VM oder dein Handy nötig sind — in der
Reihenfolge, in der sie Sinn ergeben. Nach jedem Block steht, wie du
prüfst, dass es geklappt hat.

Zeitbedarf insgesamt: ca. 45 Minuten, davon 20 min Warten.

---

## 0. Vorbereitung — Token erzeugen

Ein Secret, das App, Function und Agent teilen. In **Cloud Shell**:

```bash
export API_TOKEN=$(openssl rand -hex 32)
echo "$API_TOKEN"
```

**Notier ihn dir.** Du brauchst ihn in Schritt 2 und 5. Nicht in Git,
nicht in einen Chat.

---

## 1. Firestore aktivieren

Einmalig pro Projekt. In **Cloud Shell**:

```bash
gcloud services enable firestore.googleapis.com \
  --project=gen-lang-client-0146272192

gcloud firestore databases create \
  --location=europe-west3 \
  --project=gen-lang-client-0146272192
```

Falls „already exists" kommt: gut, dann war sie schon da.

**Prüfen:**
```bash
gcloud firestore databases list --project=gen-lang-client-0146272192
```

---

## 2. Backend deployen

Repo holen und deployen. In **Cloud Shell**:

```bash
cd ~
rm -rf Kevke-sotf
git clone -b claude/sotf-server-setup-llure1 \
  https://github.com/ollyp2/Kevke.git Kevke-sotf

cd Kevke-sotf/sotf/backend
export API_TOKEN=<dein Token aus Schritt 0>
bash deploy.sh
```

Das Skript gibt am Ende die **Base-URL** aus. Sieht so aus:
`https://sotf-control-xxxxx-ey.a.run.app`

**Notier dir die URL.** Brauchst du in Schritt 6.

**Prüfen:**
```bash
curl -H "Authorization: Bearer $API_TOKEN" <BASE_URL>/servers
```
Erwartet: `{"servers": []}` — leer ist korrekt, wir seeden gleich.

---

## 3. Berechtigungen für die Function

Die Function muss die VM starten/stoppen und Firestore lesen dürfen.

```bash
PROJECT=gen-lang-client-0146272192
PROJNUM=$(gcloud projects describe $PROJECT --format='value(projectNumber)')
SA="${PROJNUM}-compute@developer.gserviceaccount.com"

gcloud projects add-iam-policy-binding $PROJECT \
  --member="serviceAccount:${SA}" \
  --role="roles/compute.instanceAdmin.v1"

gcloud projects add-iam-policy-binding $PROJECT \
  --member="serviceAccount:${SA}" \
  --role="roles/datastore.user"
```

---

## 4. Server registrieren

Damit das Backend weiß, welche VM es steuert. In **Cloud Shell**:

```bash
pip install --quiet google-cloud-firestore

python3 - <<'PY'
from google.cloud import firestore
db = firestore.Client(project="gen-lang-client-0146272192")
db.collection("servers").document("sotf-main").set({
    "name": "Kevkes SotF",
    "game": "sons-of-the-forest",
    "zone": "europe-west3-a",
    "instance": "sotf-server",
    "logo": "sotf",
    "hourlyRateEur": 0.17,
    "live": {},
}, merge=True)
print("registriert")
PY
```

**Prüfen:**
```bash
curl -H "Authorization: Bearer $API_TOKEN" <BASE_URL>/servers
curl -H "Authorization: Bearer $API_TOKEN" "<BASE_URL>/server/status?id=sotf-main"
```
Der zweite Aufruf muss `"state": "RUNNING"` oder `"TERMINATED"` zeigen.
**Wenn hier schon RUNNING/TERMINATED steht, funktioniert die halbe Kette.**

---

## 5. Agent auf der VM installieren

### 5a. Erst die Berechtigungsfrage klären

Deine VM hat eingeschränkte Service-Account-Scopes — dort schlägt gcloud
fehl, und der Agent käme auch nicht an Firestore. Zwei Wege:

**Weg A (sauber, VM muss einmal aus):**
```bash
gcloud compute instances stop sotf-server --zone=europe-west3-a \
  --project=gen-lang-client-0146272192

gcloud compute instances set-service-account sotf-server \
  --zone=europe-west3-a --project=gen-lang-client-0146272192 \
  --scopes=https://www.googleapis.com/auth/cloud-platform

gcloud compute instances start sotf-server --zone=europe-west3-a \
  --project=gen-lang-client-0146272192
```

**Weg B (kein Neustart, dafür Key-Datei auf der VM):**
```bash
# lokal
gcloud iam service-accounts keys create sotf-agent-sa.json \
  --iam-account="${PROJNUM}-compute@developer.gserviceaccount.com" \
  --project=$PROJECT

gcloud compute scp sotf-agent-sa.json sotf-server:~ --zone=europe-west3-a
gcloud compute ssh sotf-server --zone=europe-west3-a --command='
  sudo mv ~/sotf-agent-sa.json /etc/sotf-agent-sa.json
  sudo chmod 600 /etc/sotf-agent-sa.json'
```

**Empfehlung: Weg A.** Key-Dateien auf einer VM sind ein Dauerrisiko.

### 5b. Agent installieren

```bash
gcloud compute ssh sotf-server --zone=europe-west3-a

# auf der VM:
cd ~
rm -rf Kevke-sotf
git clone -b claude/sotf-server-setup-llure1 \
  https://github.com/ollyp2/Kevke.git Kevke-sotf
sudo bash ~/Kevke-sotf/sotf/agent/install.sh
```

**Prüfen:**
```bash
sudo systemctl status sotf-agent --no-pager
sudo journalctl -u sotf-agent -n 30 --no-pager
```
Erwartet: `active (running)` und Zeilen wie `[sotf-agent] start server_id=…`.
Nach ~1 Minute:
```bash
curl -H "Authorization: Bearer $API_TOKEN" "<BASE_URL>/metrics?id=sotf-main&range=15m"
```
Muss CPU-Werte liefern. **Wenn ja: Agent-Kette steht.**

---

## 6. APK bauen

Ich habe eine GitHub-Action hinterlegt, damit du kein Android Studio
brauchst.

1. Auf GitHub: **Actions** → **Build Server Control APK** → **Run workflow**
   → Branch `claude/sotf-server-setup-llure1` → Start.
2. Wenn der Lauf grün ist: unten unter **Artifacts** liegt
   `server-control-debug.zip`.
3. Herunterladen, entpacken, die `.apk` aufs S26 Ultra kopieren.
4. Auf dem Handy installieren — Android fragt nach „Installation aus
   unbekannten Quellen", das musst du für deinen Datei-Manager erlauben.

**Alternative mit Android Studio:** Projekt `sotf/android` öffnen,
Gradle synchronisieren, Run auf dem angeschlossenen Gerät.

---

## 7. App verbinden

Beim ersten Start fragt die App nach drei Dingen:

| Feld | Wert |
|---|---|
| Name | `Kevkes SotF` (frei wählbar) |
| Backend-URL | die Base-URL aus Schritt 2 |
| API-Token | dein Token aus Schritt 0 |

Danach: Server aus der Liste wählen → Home-Screen mit dem runden Knopf.

**Erster Test:** die Status-Lampe muss den echten Zustand zeigen.
VM läuft → grüner Ring, „Online". VM aus → grauer Ring, „Aus".

---

## Entscheidungen, die ich von dir brauche

Diese Punkte stehen im DEVBOARD als Blocker und bestimmen, was ich als
nächstes baue.

### E1 — Konsolen-Befehle (wichtigster Punkt)
**Give Item und Teleport funktionieren aktuell nur als Copy-Paste-Hilfe.**
Die App baut den Befehl, du fügst ihn im Spiel per F1 ein. Grund: der
SotF-Dedicated-Server hat kein RCON, es gibt keinen offiziellen Weg,
ihm von außen Befehle zu schicken.

Optionen:
- **(a) So lassen** — App als Befehlsgenerator. Funktioniert sofort,
  kostet dich zwei Taps mehr.
- **(b) RedLoader-Mod** auf dem Server installieren. Bringt eine
  Server-Konsole mit. Bedeutet: anderes Docker-Image, mehr Wartung,
  kann bei SotF-Updates brechen.
- **(c) stdin-Experiment** — ich teste, ob der Container Befehle über
  `docker attach` annimmt. 20 Minuten Aufwand, Ausgang offen.

**Meine Empfehlung: erst (c) testen, bei Misserfolg (a) behalten.**

### E2 — Billing-Schlüssel
Aktuell rechne ich pro **Steam-ID** ab, nicht pro IP — Steam-IDs sind
stabil, IPs wechseln bei jedem DSL-Reconnect. Die IP wird trotzdem
mitgeschrieben und angezeigt. Passt das so, oder willst du IP-basiert?

### E3 — Kostenquelle
Ich rechne `Laufzeit × 0,17 €/h`. Das ist sofort verfügbar und für
eure Zwecke genau genug. Die echte Cloud-Billing-API bräuchte einen
BigQuery-Export und hat ~1 Tag Verzögerung. Soll ich das später ergänzen?

### E4 — Welt-Upload
Der Upload-Button ist noch ein Platzhalter. Sauber gelöst bräuchte es
einen GCS-Bucket plus signierte Upload-URLs. Willst du das, oder reicht
dir „ZIP per scp auf die VM, dann in der App den Slot aktivieren"?

---

## Wenn etwas nicht klappt

**`curl` gibt 401** → Token stimmt nicht überein. In Schritt 2 gesetzter
Wert muss exakt dem in der App entsprechen.

**`/server/status` gibt UNKNOWN** → IAM-Rolle aus Schritt 3 fehlt oder
ist noch nicht propagiert (kann 2 Minuten dauern).

**`/metrics` bleibt leer** → Agent läuft nicht oder kommt nicht an
Firestore. `sudo journalctl -u sotf-agent -n 50 --no-pager` zeigt warum.
Meist: Berechtigung aus Schritt 5a fehlt.

**APK-Build schlägt fehl** → schick mir den Log-Ausschnitt aus der
GitHub-Action, das sind meist Abhängigkeitsversionen die ich anpasse.

**App zeigt „Netzwerkfehler"** → Base-URL falsch (Tippfehler, fehlendes
`https://`) oder die Function ist nicht `--allow-unauthenticated`.
