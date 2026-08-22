"""sotf-control — HTTP Cloud Function (Gen2).

Single entry point, path-based routing. See ../api-spec.md.

Firestore layout:
  servers/{serverId}                      config doc
  servers/{serverId}/metrics/{ts}         cpu/mem samples pushed by the agent
  servers/{serverId}/sessions/{sessionId} player connect/disconnect spans
  servers/{serverId}/uptime/{spanId}      vm running spans
  servers/{serverId}/jobs/{jobId}         command queue for the agent
  servers/{serverId}/backups/{name}       backup index
"""

import hmac
import json
import os
import re
import uuid
from datetime import datetime, timedelta, timezone

import functions_framework
from google.cloud import firestore
from googleapiclient import discovery

API_TOKEN = os.environ["API_TOKEN"]
PROJECT_ID = os.environ["PROJECT_ID"]
DEFAULT_HOURLY_RATE_EUR = float(os.environ.get("HOURLY_RATE_EUR", "0.17"))

db = firestore.Client(project=PROJECT_ID)
_compute = None


def compute():
    global _compute
    if _compute is None:
        _compute = discovery.build("compute", "v1", cache_discovery=False)
    return _compute


def now():
    return datetime.now(timezone.utc)


def iso(dt):
    if dt is None:
        return None
    if isinstance(dt, str):
        return dt
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_iso(s):
    if isinstance(s, datetime):
        return s if s.tzinfo else s.replace(tzinfo=timezone.utc)
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def err(slug, detail="", code=400):
    return (json.dumps({"error": slug, "detail": detail}), code,
            {"Content-Type": "application/json"})


def ok(payload, code=200):
    return (json.dumps(payload), code, {"Content-Type": "application/json"})


def authorized(request):
    header = request.headers.get("Authorization", "")
    if not header.startswith("Bearer "):
        return False
    return hmac.compare_digest(header[7:], API_TOKEN)


# --------------------------------------------------------------------------
# server registry
# --------------------------------------------------------------------------

def server_doc(server_id):
    snap = db.collection("servers").document(server_id).get()
    return snap.to_dict() if snap.exists else None


def list_servers():
    out = []
    for snap in db.collection("servers").stream():
        d = snap.to_dict()
        out.append({
            "id": snap.id,
            "name": d.get("name", snap.id),
            "game": d.get("game", "unknown"),
            "zone": d.get("zone"),
            "instance": d.get("instance"),
            "logo": d.get("logo", "generic"),
        })
    return out


# --------------------------------------------------------------------------
# compute engine
# --------------------------------------------------------------------------

def instance_state(cfg):
    try:
        inst = compute().instances().get(
            project=PROJECT_ID, zone=cfg["zone"], instance=cfg["instance"]
        ).execute()
    except Exception as exc:  # noqa: BLE001 - surfaced to the client as UNKNOWN
        return {"state": "UNKNOWN", "externalIp": None, "detail": str(exc)}

    ip = None
    for nic in inst.get("networkInterfaces", []):
        for ac in nic.get("accessConfigs", []):
            if ac.get("natIP"):
                ip = ac["natIP"]
    return {
        "state": inst.get("status", "UNKNOWN"),
        "externalIp": ip,
        "lastStart": inst.get("lastStartTimestamp"),
        "lastStop": inst.get("lastStopTimestamp"),
    }


# --------------------------------------------------------------------------
# jobs — the agent polls these
# --------------------------------------------------------------------------

def enqueue(server_id, kind, args=None):
    job_id = "j_" + uuid.uuid4().hex[:10]
    db.collection("servers").document(server_id).collection("jobs").document(job_id).set({
        "kind": kind,
        "args": args or {},
        "state": "queued",
        "createdAt": now(),
        "finishedAt": None,
        "result": None,
        "error": None,
    })
    return job_id


def job_status(server_id, job_id):
    snap = (db.collection("servers").document(server_id)
              .collection("jobs").document(job_id).get())
    if not snap.exists:
        return None
    d = snap.to_dict()
    return {
        "id": job_id,
        "kind": d.get("kind"),
        "state": d.get("state"),
        "createdAt": iso(d.get("createdAt")),
        "finishedAt": iso(d.get("finishedAt")),
        "result": d.get("result"),
        "error": d.get("error"),
    }


# --------------------------------------------------------------------------
# sessions & billing
# --------------------------------------------------------------------------

RANGE_PRESETS = {
    "day": timedelta(days=1),
    "week": timedelta(days=7),
    "month": timedelta(days=30),
    "quarter": timedelta(days=91),
    "year": timedelta(days=365),
    "15m": timedelta(minutes=15),
    "1h": timedelta(hours=1),
    "6h": timedelta(hours=6),
    "24h": timedelta(hours=24),
    "7d": timedelta(days=7),
}


def resolve_range(request):
    rng = request.args.get("range", "month")
    end = now()
    if rng == "custom":
        frm = request.args.get("from")
        to = request.args.get("to")
        if not frm or not to:
            raise ValueError("custom range needs from and to")
        return parse_iso(frm), parse_iso(to)
    if rng == "last-month":
        first_this = end.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        last_end = first_this - timedelta(seconds=1)
        return last_end.replace(day=1, hour=0, minute=0, second=0, microsecond=0), last_end
    if rng not in RANGE_PRESETS:
        raise ValueError(f"unknown range {rng}")
    return end - RANGE_PRESETS[rng], end


def clamp_spans(spans, start, end):
    """Trim [from,to] spans to the window, dropping anything outside."""
    out = []
    for s in spans:
        a = max(parse_iso(s["from"]), start)
        b = min(parse_iso(s["to"]) if s.get("to") else end, end)
        if b > a:
            out.append((a, b, s))
    return out


def split_cost(uptime_spans, player_spans, start, end, rate_eur_h):
    """Divide uptime cost among players present in each sub-interval.

    Builds the set of instants where presence changes, walks consecutive
    pairs, and splits each slice's cost by the number of players online.
    Slices with nobody online stay unattributed.
    """
    up = clamp_spans(uptime_spans, start, end)
    pl = clamp_spans(player_spans, start, end)

    edges = set()
    for a, b, _ in up:
        edges.add(a)
        edges.add(b)
    for a, b, _ in pl:
        edges.add(a)
        edges.add(b)
    marks = sorted(edges)

    per_player = {}
    unattributed_seconds = 0.0
    total_seconds = 0.0

    for i in range(len(marks) - 1):
        a, b = marks[i], marks[i + 1]
        seconds = (b - a).total_seconds()
        if seconds <= 0:
            continue
        if not any(sa <= a and b <= sb for sa, sb, _ in up):
            continue  # VM was off, nothing to bill
        total_seconds += seconds

        present = [s for sa, sb, s in pl if sa <= a and b <= sb]
        if not present:
            unattributed_seconds += seconds
            continue
        share = seconds / len(present)
        for s in present:
            key = s.get("steamId") or s.get("ip") or "unknown"
            slot = per_player.setdefault(key, {
                "steamId": s.get("steamId"),
                "name": s.get("name"),
                "lastIp": s.get("ip"),
                "sessionSeconds": 0.0,
                "billedSeconds": 0.0,
            })
            slot["billedSeconds"] += share
            slot["sessionSeconds"] += seconds
            if s.get("name"):
                slot["name"] = s["name"]
            if s.get("ip"):
                slot["lastIp"] = s["ip"]

    rate_per_second = rate_eur_h / 3600.0
    players = []
    for slot in per_player.values():
        players.append({
            "steamId": slot["steamId"],
            "name": slot["name"],
            "lastIp": slot["lastIp"],
            "sessionSeconds": round(slot["sessionSeconds"]),
            "shareEur": round(slot["billedSeconds"] * rate_per_second, 2),
        })
    players.sort(key=lambda p: p["shareEur"], reverse=True)

    return {
        "totalEur": round(total_seconds * rate_per_second, 2),
        "uptimeSeconds": round(total_seconds),
        "hourlyRateEur": rate_eur_h,
        "perPlayer": players,
        "unattributedEur": round(unattributed_seconds * rate_per_second, 2),
    }


def read_spans(server_id, collection, start, end):
    col = (db.collection("servers").document(server_id).collection(collection)
             .where(filter=firestore.FieldFilter("from", "<=", end)))
    out = []
    for snap in col.stream():
        d = snap.to_dict()
        to = d.get("to")
        if to and parse_iso(iso(to)) < start:
            continue
        out.append({
            "from": iso(d.get("from")),
            "to": iso(to),
            "steamId": d.get("steamId"),
            "name": d.get("name"),
            "ip": d.get("ip"),
        })
    return out


# --------------------------------------------------------------------------
# metrics
# --------------------------------------------------------------------------

BUCKET_SECONDS = {
    "15m": 30, "1h": 60, "6h": 300, "24h": 900, "7d": 3600,
    "day": 900, "week": 3600, "month": 21600,
}


def read_metrics(server_id, start, end, bucket_seconds):
    col = (db.collection("servers").document(server_id).collection("metrics")
             .where(filter=firestore.FieldFilter("t", ">=", start))
             .where(filter=firestore.FieldFilter("t", "<=", end))
             .order_by("t"))

    buckets = {}
    latest = None
    for snap in col.stream():
        d = snap.to_dict()
        t = parse_iso(iso(d["t"]))
        key = int(t.timestamp() // bucket_seconds) * bucket_seconds
        b = buckets.setdefault(key, {"cpu": 0.0, "mem": 0.0, "n": 0})
        b["cpu"] += float(d.get("cpu", 0))
        b["mem"] += float(d.get("mem", 0))
        b["n"] += 1
        latest = d

    series = []
    for key in sorted(buckets):
        b = buckets[key]
        series.append({
            "t": iso(datetime.fromtimestamp(key, timezone.utc)),
            "cpu": round(b["cpu"] / b["n"], 1),
            "mem": round(b["mem"] / b["n"], 1),
        })

    current = {"cpuPercent": None, "memPercent": None,
               "memUsedMb": None, "memTotalMb": None}
    if latest:
        current = {
            "cpuPercent": round(float(latest.get("cpu", 0)), 1),
            "memPercent": round(float(latest.get("mem", 0)), 1),
            "memUsedMb": latest.get("memUsedMb"),
            "memTotalMb": latest.get("memTotalMb"),
        }
    return {"current": current, "series": series}


# --------------------------------------------------------------------------
# routes
# --------------------------------------------------------------------------

def route_server_status(request, server_id, cfg):
    state = instance_state(cfg)
    live = server_doc(server_id).get("live", {}) or {}
    uptime = None
    if state.get("lastStart") and state["state"] == "RUNNING":
        uptime = (now() - parse_iso(state["lastStart"])).total_seconds()

    return ok({
        "id": server_id,
        "state": state["state"],
        "externalIp": state.get("externalIp"),
        "lastStart": iso(state.get("lastStart")),
        "lastStop": iso(state.get("lastStop")),
        "uptimeSeconds": round(uptime) if uptime else None,
        "gameReady": live.get("gameReady", False),
        "playersOnline": len(live.get("players", [])),
        "idleShutdownIn": live.get("idleShutdownIn"),
    })


def route_server_start(server_id, cfg):
    compute().instances().start(
        project=PROJECT_ID, zone=cfg["zone"], instance=cfg["instance"]
    ).execute()
    db.collection("servers").document(server_id).collection("uptime").add({
        "from": now(), "to": None,
    })
    return ok({"state": "STAGING",
               "message": "Server faehrt hoch, ca. 3 Minuten."}, 202)


def route_server_stop(server_id, cfg, body):
    live = (server_doc(server_id).get("live") or {})
    if live.get("players") and not body.get("force"):
        return err("players_online",
                   f"{len(live['players'])} Spieler online. force=true zum Erzwingen.",
                   409)
    compute().instances().stop(
        project=PROJECT_ID, zone=cfg["zone"], instance=cfg["instance"]
    ).execute()
    col = db.collection("servers").document(server_id).collection("uptime")
    for snap in col.where(filter=firestore.FieldFilter("to", "==", None)).stream():
        snap.reference.update({"to": now()})
    return ok({"state": "STOPPING"}, 202)


def route_backups_list(server_id):
    col = db.collection("servers").document(server_id).collection("backups")
    out = []
    for snap in col.order_by("createdAt", direction=firestore.Query.DESCENDING).stream():
        d = snap.to_dict()
        out.append({
            "name": snap.id,
            "createdAt": iso(d.get("createdAt")),
            "sizeBytes": d.get("sizeBytes"),
            "slot": d.get("slot"),
            "worldName": d.get("worldName"),
            "gameDays": d.get("gameDays"),
            "source": d.get("source", "manual"),
        })
    return ok({"backups": out})


def route_billing(request, server_id, cfg):
    try:
        start, end = resolve_range(request)
    except ValueError as exc:
        return err("bad_range", str(exc))

    uptime = read_spans(server_id, "uptime", start, end)
    sessions = read_spans(server_id, "sessions", start, end)
    rate = float(cfg.get("hourlyRateEur", DEFAULT_HOURLY_RATE_EUR))

    payload = split_cost(uptime, sessions, start, end, rate)
    payload["range"] = {"from": iso(start), "to": iso(end)}
    return ok(payload)


def route_metrics(request, server_id):
    try:
        start, end = resolve_range(request)
    except ValueError as exc:
        return err("bad_range", str(exc))
    bucket = BUCKET_SECONDS.get(request.args.get("range", "1h"), 60)
    return ok(read_metrics(server_id, start, end, bucket))


def route_sotf_players(server_id):
    live = (server_doc(server_id).get("live") or {})
    return ok({"online": live.get("players", [])})


def route_sotf_worlds(server_id):
    live = (server_doc(server_id).get("live") or {})
    return ok({
        "activeSlot": live.get("activeSlot"),
        "slots": live.get("slots", []),
    })


def route_sotf_config(server_id):
    live = (server_doc(server_id).get("live") or {})
    return ok({"config": live.get("config", {}), "schema": CONFIG_SCHEMA})


# Which knobs the app can render, and whether they bite on an existing world.
# CustomGameModeSettings are baked into a world at creation — the game only
# reads them for a fresh save, so the app warns instead of silently lying.
CONFIG_SCHEMA = [
    {"key": "ServerName", "type": "string", "label": "Servername",
     "appliesToExistingWorld": True},
    {"key": "Password", "type": "string", "secret": True, "label": "Passwort",
     "appliesToExistingWorld": True},
    {"key": "MaxPlayers", "type": "int", "min": 1, "max": 8, "label": "Max. Spieler",
     "appliesToExistingWorld": True},
    {"key": "SaveInterval", "type": "int", "min": 60, "max": 3600,
     "label": "Autosave-Intervall (s)", "appliesToExistingWorld": True},
    {"key": "GameMode", "type": "enum",
     "values": ["Normal", "Hard", "HardSurvival", "Peaceful", "Custom"],
     "label": "Spielmodus", "appliesToExistingWorld": True},

    {"key": "GameSettings.Structure.Damage", "type": "bool",
     "label": "Strukturschaden", "hint": "Aus = Basis unzerstoerbar",
     "appliesToExistingWorld": True},
    {"key": "GameSettings.Gameplay.TreeRegrowth", "type": "bool",
     "label": "Baeume wachsen nach", "appliesToExistingWorld": True},

    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemyHealth", "type": "enum",
     "values": ["Low", "Normal", "High", "VeryHigh"], "label": "Gegner-Leben",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemyDamage", "type": "enum",
     "values": ["Low", "Normal", "High", "VeryHigh"], "label": "Gegner-Schaden",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemyArmour", "type": "enum",
     "values": ["Low", "Normal", "High", "VeryHigh"], "label": "Gegner-Ruestung",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemyAggression", "type": "enum",
     "values": ["Low", "Normal", "High", "VeryHigh"], "label": "Gegner-Aggression",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Vail.EnemySpawn", "type": "bool",
     "label": "Gegner spawnen", "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Vail.AnimalSpawnRate", "type": "enum",
     "values": ["Low", "Normal", "High"], "label": "Tier-Spawnrate",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Survival.SingleUseContainers",
     "type": "bool", "label": "Container einmalig",
     "hint": "Aus = Loot kommt nach Neustart wieder", "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Survival.ReducedFoodInContainers",
     "type": "bool", "label": "Weniger Nahrung in Containern",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Survival.BuildingResistance",
     "type": "enum", "values": ["Weak", "Normal", "Strong", "Impregnable"],
     "label": "Gebaeude-Widerstand", "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Survival.ColdPenalties", "type": "enum",
     "values": ["Off", "Normal", "High"], "label": "Kaelte-Strafen",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Survival.PlayerStatsDamage",
     "type": "enum", "values": ["Off", "Normal", "High"], "label": "Stat-Schaden",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Multiplayer.PvpDamage", "type": "enum",
     "values": ["Off", "Low", "Normal", "High"], "label": "PvP-Schaden",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Environment.DayLength", "type": "enum",
     "values": ["Short", "Default", "Long"], "label": "Taglaenge",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Environment.SeasonLength", "type": "enum",
     "values": ["Short", "Default", "Long"], "label": "Jahreszeit-Laenge",
     "appliesToExistingWorld": False},
    {"key": "CustomGameModeSettings.GameSetting.Environment.PrecipitationFrequency",
     "type": "enum", "values": ["Low", "Default", "High"], "label": "Niederschlag",
     "appliesToExistingWorld": False},
]


# The dedicated server has no RCON. Until a console channel exists (see
# DEVBOARD B1), these endpoints hand the command back for manual entry.
def manual_command(command, hint="Konsole mit F1 oeffnen und Befehl eingeben"):
    return ok({"mode": "manual", "command": command, "hint": hint})


ROUTES_NEEDING_SERVER = {
    "/server/status", "/server/start", "/server/stop", "/backups",
    "/backups/create", "/backups/restore", "/backups/delete", "/metrics",
    "/billing", "/sotf/worlds", "/sotf/worlds/activate", "/sotf/players",
    "/sotf/give", "/sotf/teleport", "/sotf/config",
}


@functions_framework.http
def sotf_control(request):
    if request.method == "OPTIONS":
        return ("", 204, {
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
            "Access-Control-Allow-Headers": "Authorization,Content-Type",
        })

    if not authorized(request):
        return err("unauthorized", "Bearer token fehlt oder ist falsch", 401)

    path = "/" + request.path.strip("/").removeprefix("sotf-control").strip("/")
    path = re.sub(r"/+", "/", path).rstrip("/") or "/"
    body = request.get_json(silent=True) or {}

    if path in ("/", "/servers"):
        return ok({"servers": list_servers()})

    if path.startswith("/jobs/"):
        job_id = path.split("/")[-1]
        server_id = request.args.get("id") or body.get("id")
        if not server_id:
            return err("missing_id", "id-Parameter fehlt")
        status = job_status(server_id, job_id)
        return ok(status) if status else err("not_found", job_id, 404)

    server_id = request.args.get("id") or body.get("id")
    if path in ROUTES_NEEDING_SERVER:
        if not server_id:
            return err("missing_id", "id-Parameter fehlt")
        cfg = server_doc(server_id)
        if not cfg:
            return err("not_found", f"Server {server_id} unbekannt", 404)
    else:
        cfg = None

    if path == "/server/status":
        return route_server_status(request, server_id, cfg)
    if path == "/server/start":
        return route_server_start(server_id, cfg)
    if path == "/server/stop":
        return route_server_stop(server_id, cfg, body)

    if path == "/backups":
        return route_backups_list(server_id)
    if path == "/backups/create":
        return ok({"job": enqueue(server_id, "backup.create",
                                  {"label": body.get("label")})}, 202)
    if path == "/backups/restore":
        if not body.get("name"):
            return err("missing_name", "name des Backups fehlt")
        return ok({"job": enqueue(server_id, "backup.restore",
                                  {"name": body["name"]})}, 202)
    if path == "/backups/delete":
        if not body.get("confirm"):
            return err("confirmation_required", "confirm=true noetig")
        return ok({"job": enqueue(server_id, "backup.delete",
                                  {"name": body.get("name")})}, 202)

    if path == "/metrics":
        return route_metrics(request, server_id)
    if path == "/billing":
        return route_billing(request, server_id, cfg)

    if path == "/sotf/worlds":
        return route_sotf_worlds(server_id)
    if path == "/sotf/worlds/activate":
        slot = body.get("slot")
        if slot not in (1, 2, 3, 4, 5):
            return err("bad_slot", "slot muss 1-5 sein")
        return ok({"job": enqueue(server_id, "sotf.world.activate",
                                  {"slot": slot})}, 202)
    if path == "/sotf/players":
        return route_sotf_players(server_id)
    if path == "/sotf/config":
        if request.method == "GET":
            return route_sotf_config(server_id)
        return ok({"job": enqueue(server_id, "sotf.config.patch", {
            "changes": body.get("changes", {}),
            "restart": bool(body.get("restart", True)),
        })}, 202)

    if path == "/sotf/give":
        item_id = body.get("itemId")
        count = int(body.get("count", 1))
        if item_id is None:
            return err("missing_item", "itemId fehlt")
        return manual_command(f"give {item_id} {count}")

    if path == "/sotf/teleport":
        target = body.get("target", {})
        kind = target.get("kind")
        if kind == "xyz":
            cmd = f"goto {target.get('x')} {target.get('y')} {target.get('z')}"
        elif kind == "player":
            cmd = f"gotoplayer {target.get('steamId')}"
        elif kind == "poi":
            cmd = f"goto {target.get('poi')}"
        else:
            return err("bad_target", "target.kind muss player|xyz|poi sein")
        return manual_command(cmd)

    return err("not_found", f"Unbekannter Pfad: {path}", 404)
