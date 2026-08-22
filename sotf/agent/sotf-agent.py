#!/usr/bin/env python3
"""sotf-agent — runs on the game VM, talks to Firestore.

Three loops in one process:
  1. metrics   — sample container CPU/RAM every SAMPLE_SECONDS, push to Firestore
  2. logtail   — follow `docker logs -f`, turn connect/disconnect lines into
                 session spans (the raw material for per-player billing)
  3. jobs      — poll the job queue and execute backup/world/config commands

Config via /etc/sotf-agent.env (see install.sh).
"""

import json
import os
import re
import shutil
import signal
import subprocess
import threading
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from google.cloud import firestore

SERVER_ID = os.environ.get("SOTF_SERVER_ID", "sotf-main")
PROJECT_ID = os.environ["SOTF_PROJECT_ID"]
CONTAINER = os.environ.get("SOTF_CONTAINER", "sotf-server")
SOTF_DIR = Path(os.environ.get("SOTF_DIR", "/home/kradtke79/sotf"))
SAMPLE_SECONDS = int(os.environ.get("SOTF_SAMPLE_SECONDS", "30"))
JOB_POLL_SECONDS = int(os.environ.get("SOTF_JOB_POLL_SECONDS", "10"))
METRIC_RETENTION_DAYS = int(os.environ.get("SOTF_METRIC_RETENTION_DAYS", "30"))

USERDATA = SOTF_DIR / "userdata"
BACKUP_ROOT = SOTF_DIR / "backups"
CFG_PATH = USERDATA / "dedicatedserver.cfg"
SAVE_ROOT = USERDATA / "Saves" / "DedicatedServer" / "Multiplayer"

db = firestore.Client(project=PROJECT_ID)
server_ref = db.collection("servers").document(SERVER_ID)
stop_event = threading.Event()


def now():
    return datetime.now(timezone.utc)


def log(*parts):
    print(f"[sotf-agent] {' '.join(str(p) for p in parts)}", flush=True)


def sh(*args, check=True, capture=True):
    return subprocess.run(args, check=check,
                          capture_output=capture, text=True)


# --------------------------------------------------------------------------
# metrics
# --------------------------------------------------------------------------

def sample_container():
    """One `docker stats` sample: CPU% and memory of the game container."""
    try:
        out = sh("docker", "stats", "--no-stream", "--format",
                 "{{.CPUPerc}}|{{.MemUsage}}|{{.MemPerc}}", CONTAINER).stdout.strip()
    except subprocess.CalledProcessError:
        return None
    if not out:
        return None

    cpu_s, mem_usage, mem_perc = out.split("|")
    used_s = mem_usage.split("/")[0].strip()
    total_s = mem_usage.split("/")[1].strip()
    return {
        "t": now(),
        "cpu": float(cpu_s.rstrip("%")),
        "mem": float(mem_perc.rstrip("%")),
        "memUsedMb": to_mb(used_s),
        "memTotalMb": to_mb(total_s),
    }


def to_mb(text):
    m = re.match(r"([\d.]+)\s*([KMGT]?i?B)", text, re.I)
    if not m:
        return None
    value, unit = float(m.group(1)), m.group(2).upper().rstrip("B").rstrip("I")
    factor = {"": 1 / 1_048_576, "K": 1 / 1024, "M": 1, "G": 1024, "T": 1_048_576}
    return round(value * factor.get(unit, 1))


def metrics_loop():
    while not stop_event.is_set():
        sample = sample_container()
        if sample:
            server_ref.collection("metrics").add(sample)
        stop_event.wait(SAMPLE_SECONDS)


def prune_loop():
    """Drop metric docs past the retention window so Firestore stays small."""
    while not stop_event.is_set():
        cutoff = now().timestamp() - METRIC_RETENTION_DAYS * 86400
        cutoff_dt = datetime.fromtimestamp(cutoff, timezone.utc)
        old = (server_ref.collection("metrics")
               .where(filter=firestore.FieldFilter("t", "<", cutoff_dt))
               .limit(400).stream())
        n = 0
        for snap in old:
            snap.reference.delete()
            n += 1
        if n:
            log(f"pruned {n} old metric docs")
        stop_event.wait(3600)


# --------------------------------------------------------------------------
# log tail -> sessions + live state
# --------------------------------------------------------------------------

RE_AUTH = re.compile(
    r"Steam auth successful for client (\d+) with steam id (\d{17}), username (.+)")
RE_UNREGISTER = re.compile(
    r"Unregistering client (\d+) with steam id (\d{17})")
RE_ENDPOINT = re.compile(r"EndPoint ([\d.]+):(\d+)")
RE_LOADED = re.compile(r"Dedicated server loaded")
RE_IDLE = re.compile(r"Server empty, idling")
RE_AUTOSAVE = re.compile(r"Autosave complete\. Time: (.+?) Slot: (\d+)")

live_players = {}       # clientId -> player dict
open_sessions = {}      # steamId -> firestore doc ref
recent_ip = {}          # clientId -> ip seen near the auth line


def push_live(**patch):
    server_ref.set({"live": patch}, merge=True)


def on_connect(client_id, steam_id, name):
    player = {
        "clientId": int(client_id),
        "steamId": steam_id,
        "name": name.strip(),
        "ip": recent_ip.get(client_id),
        "connectedAt": now(),
    }
    live_players[client_id] = player
    doc = server_ref.collection("sessions").document()
    doc.set({
        "steamId": steam_id,
        "name": name.strip(),
        "ip": player["ip"],
        "from": now(),
        "to": None,
    })
    open_sessions[steam_id] = doc
    log(f"connect {name.strip()} ({steam_id})")
    push_live(players=[serializable(p) for p in live_players.values()])


def on_disconnect(client_id, steam_id):
    live_players.pop(client_id, None)
    doc = open_sessions.pop(steam_id, None)
    if doc:
        doc.update({"to": now()})
    log(f"disconnect {steam_id}")
    push_live(players=[serializable(p) for p in live_players.values()])


def serializable(player):
    out = dict(player)
    if isinstance(out.get("connectedAt"), datetime):
        out["connectedAt"] = out["connectedAt"].isoformat().replace("+00:00", "Z")
    return out


def logtail_loop():
    while not stop_event.is_set():
        try:
            proc = subprocess.Popen(
                ["docker", "logs", "-f", "--tail", "5", CONTAINER],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        except FileNotFoundError:
            log("docker not found, logtail disabled")
            return

        for line in proc.stdout:
            if stop_event.is_set():
                proc.terminate()
                return

            m = RE_ENDPOINT.search(line)
            if m:
                for cid in live_players:
                    recent_ip.setdefault(cid, m.group(1))

            m = RE_AUTH.search(line)
            if m:
                client_id, steam_id, name = m.groups()
                ip = RE_ENDPOINT.search(line)
                if ip:
                    recent_ip[client_id] = ip.group(1)
                on_connect(client_id, steam_id, name)
                continue

            m = RE_UNREGISTER.search(line)
            if m:
                on_disconnect(m.group(1), m.group(2))
                continue

            if RE_LOADED.search(line):
                push_live(gameReady=True)
                refresh_world_state()
                continue

            if RE_IDLE.search(line):
                push_live(players=[])
                continue

            m = RE_AUTOSAVE.search(line)
            if m:
                push_live(activeSlot=int(m.group(2)),
                          lastAutosave=m.group(1))

        proc.wait()
        log("log stream ended, retrying in 10s")
        stop_event.wait(10)


# --------------------------------------------------------------------------
# world / config introspection
# --------------------------------------------------------------------------

def read_cfg():
    if not CFG_PATH.exists():
        return {}
    try:
        return json.loads(CFG_PATH.read_text())
    except json.JSONDecodeError:
        log("dedicatedserver.cfg is not valid JSON")
        return {}


def world_slots():
    slots = []
    if not SAVE_ROOT.exists():
        return slots
    for d in sorted(SAVE_ROOT.iterdir()):
        if not d.is_dir() or not d.name.isdigit():
            continue
        save = d / "SaveData.zip"
        if not save.exists():
            continue
        name_file = next((f for f in d.glob("*.name")), None)
        slots.append({
            "slot": int(d.name),
            "dirName": d.name,
            "worldName": name_file.stem if name_file else None,
            "sizeBytes": save.stat().st_size,
            "lastSaved": datetime.fromtimestamp(
                save.stat().st_mtime, timezone.utc).isoformat().replace("+00:00", "Z"),
            "gameDays": game_days(save),
        })
    return slots


def game_days(save_zip):
    """Pull the in-game day counter out of the nested save archive."""
    try:
        with zipfile.ZipFile(save_zip) as z:
            with z.open("GameStateSaveData.json") as f:
                outer = json.load(f)
        inner = json.loads(outer["Data"]["GameState"])
        return inner.get("GameDays")
    except Exception:  # noqa: BLE001 - a save we cannot parse just has no day count
        return None


def refresh_world_state():
    cfg = read_cfg()
    push_live(
        config=cfg,
        activeSlot=cfg.get("SaveSlot"),
        slots=world_slots(),
    )


# --------------------------------------------------------------------------
# jobs
# --------------------------------------------------------------------------

def container_stop():
    sh("docker", "stop", CONTAINER)


def container_start():
    sh("docker", "start", CONTAINER)


def job_backup_create(args):
    BACKUP_ROOT.mkdir(parents=True, exist_ok=True)
    label = (args.get("label") or "").strip()
    stamp = now().strftime("%Y-%m-%d_%H%M")
    name = f"{stamp}_{slug(label)}" if label else stamp
    target = BACKUP_ROOT / f"{name}.zip"

    running = container_is_running()
    if running:
        container_stop()
    try:
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
            for path in USERDATA.rglob("*"):
                if path.is_file():
                    z.write(path, path.relative_to(USERDATA))
    finally:
        if running:
            container_start()

    cfg = read_cfg()
    slots = world_slots()
    active = next((s for s in slots if s["slot"] == cfg.get("SaveSlot")), None)
    meta = {
        "createdAt": now(),
        "sizeBytes": target.stat().st_size,
        "slot": cfg.get("SaveSlot"),
        "worldName": active["worldName"] if active else None,
        "gameDays": active["gameDays"] if active else None,
        "source": args.get("source", "manual"),
        "path": str(target),
    }
    server_ref.collection("backups").document(name).set(meta)
    return {"name": name, "sizeBytes": meta["sizeBytes"]}


def job_backup_restore(args):
    name = args["name"]
    archive = BACKUP_ROOT / f"{name}.zip"
    if not archive.exists():
        raise FileNotFoundError(f"backup {name} not found")

    job_backup_create({"label": "pre-restore", "source": "pre-restore"})

    running = container_is_running()
    if running:
        container_stop()
    try:
        staging = USERDATA.parent / "userdata.restoring"
        if staging.exists():
            shutil.rmtree(staging)
        staging.mkdir(parents=True)
        with zipfile.ZipFile(archive) as z:
            z.extractall(staging)
        retired = USERDATA.parent / f"userdata.replaced.{now():%Y%m%d%H%M%S}"
        USERDATA.rename(retired)
        staging.rename(USERDATA)
        sh("chown", "-R", "1000:1000", str(USERDATA))
    finally:
        if running:
            container_start()
    refresh_world_state()
    return {"restored": name}


def job_backup_delete(args):
    name = args["name"]
    archive = BACKUP_ROOT / f"{name}.zip"
    if archive.exists():
        archive.unlink()
    server_ref.collection("backups").document(name).delete()
    return {"deleted": name}


def job_world_activate(args):
    slot = int(args["slot"])
    cfg = read_cfg()
    cfg["SaveSlot"] = slot
    cfg["SaveMode"] = "Continue"
    job_backup_create({"label": f"pre-slot{slot}", "source": "auto"})
    write_cfg(cfg)
    restart_container()
    refresh_world_state()
    return {"activeSlot": slot}


def job_config_patch(args):
    changes = args.get("changes", {})
    cfg = read_cfg()
    for dotted, value in changes.items():
        set_dotted(cfg, dotted, value)
    job_backup_create({"label": "pre-config", "source": "auto"})
    write_cfg(cfg)
    if args.get("restart", True):
        restart_container()
    refresh_world_state()
    return {"applied": list(changes)}


def set_dotted(cfg, dotted, value):
    """Set 'GameSettings.Structure.Damage' style keys.

    Only the first segment is a real nesting level; SotF's own keys are
    flat strings that happen to contain dots, so we split once.
    """
    if "." not in dotted:
        cfg[dotted] = value
        return
    head, rest = dotted.split(".", 1)
    if head in ("GameSettings", "CustomGameModeSettings"):
        cfg.setdefault(head, {})[rest] = value
    else:
        cfg[dotted] = value


def write_cfg(cfg):
    tmp = CFG_PATH.with_suffix(".cfg.tmp")
    tmp.write_text(json.dumps(cfg, indent=2))
    tmp.replace(CFG_PATH)
    sh("chown", "1000:1000", str(CFG_PATH))


def restart_container():
    container_stop()
    container_start()


def container_is_running():
    try:
        out = sh("docker", "inspect", "-f", "{{.State.Running}}", CONTAINER,
                 check=False).stdout.strip()
        return out == "true"
    except Exception:  # noqa: BLE001
        return False


def slug(text):
    return re.sub(r"[^a-zA-Z0-9]+", "-", text).strip("-").lower()[:40]


JOB_HANDLERS = {
    "backup.create": job_backup_create,
    "backup.restore": job_backup_restore,
    "backup.delete": job_backup_delete,
    "sotf.world.activate": job_world_activate,
    "sotf.config.patch": job_config_patch,
}


def jobs_loop():
    while not stop_event.is_set():
        queued = (server_ref.collection("jobs")
                  .where(filter=firestore.FieldFilter("state", "==", "queued"))
                  .limit(5).stream())
        for snap in queued:
            job = snap.to_dict()
            kind = job.get("kind")
            handler = JOB_HANDLERS.get(kind)
            snap.reference.update({"state": "running"})
            log(f"job {snap.id} {kind} start")
            if not handler:
                snap.reference.update({
                    "state": "failed", "finishedAt": now(),
                    "error": f"unknown job kind {kind}"})
                continue
            try:
                result = handler(job.get("args", {}))
                snap.reference.update({
                    "state": "done", "finishedAt": now(), "result": result})
                log(f"job {snap.id} done: {result}")
            except Exception as exc:  # noqa: BLE001 - report failures to the app
                snap.reference.update({
                    "state": "failed", "finishedAt": now(), "error": str(exc)})
                log(f"job {snap.id} failed: {exc}")
        stop_event.wait(JOB_POLL_SECONDS)


# --------------------------------------------------------------------------

def handle_signal(signum, _frame):
    log(f"signal {signum}, shutting down")
    stop_event.set()


def main():
    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)

    log(f"start server_id={SERVER_ID} container={CONTAINER} dir={SOTF_DIR}")
    refresh_world_state()
    push_live(agentStartedAt=now().isoformat().replace("+00:00", "Z"))

    threads = [
        threading.Thread(target=metrics_loop, name="metrics", daemon=True),
        threading.Thread(target=logtail_loop, name="logtail", daemon=True),
        threading.Thread(target=jobs_loop, name="jobs", daemon=True),
        threading.Thread(target=prune_loop, name="prune", daemon=True),
    ]
    for t in threads:
        t.start()

    while not stop_event.is_set():
        time.sleep(1)

    # Close any session still open so billing does not count an endless span.
    for steam_id, doc in list(open_sessions.items()):
        doc.update({"to": now()})
    log("stopped")


if __name__ == "__main__":
    main()
