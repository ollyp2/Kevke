"""sotf-control — one function, one token, everything via URL.

Same query-string token as the original start trigger, just more actions:

    ?token=…&action=start           start the VM
    ?token=…&action=stop[&force=1]  stop it (refuses while players are on)
    ?token=…&action=status          up? joinable? who is on?
    ?token=…&action=backup          snapshot the disk
    ?token=…&action=backups         list snapshots
    ?token=…&action=restore&name=…  roll the disk back to a snapshot
    ?token=…&action=delete_backup&name=…
    ?token=…&action=billing&range=month

Every action works pasted into a browser and answers a readable line;
add &format=json (or an Accept: application/json header) for the machine
version the app uses.

Env vars (set at deploy time):
    TOKEN        shared secret, compared against ?token=
    PROJECT_ID   GCP project
    ZONE         e.g. europe-west3-a
    INSTANCE     e.g. sotf-server
    QUERY_PORT   Steam query port, default 27016
    HOURLY_RATE  EUR per running hour, default 0.17
"""

import hmac
import json
import os
import re
import socket
import time
from datetime import datetime, timedelta, timezone

import functions_framework
from googleapiclient import discovery

TOKEN = os.environ["TOKEN"]
PROJECT_ID = os.environ["PROJECT_ID"]
ZONE = os.environ.get("ZONE", "europe-west3-a")
INSTANCE = os.environ.get("INSTANCE", "sotf-server")
QUERY_PORT = int(os.environ.get("QUERY_PORT", "27016"))
HOURLY_RATE = float(os.environ.get("HOURLY_RATE", "0.17"))

SNAPSHOT_PREFIX = f"{INSTANCE}-backup"

_clients = {}


def api(name, version):
    key = (name, version)
    if key not in _clients:
        _clients[key] = discovery.build(name, version, cache_discovery=False)
    return _clients[key]


def compute():
    return api("compute", "v1")


def instance():
    return compute().instances().get(
        project=PROJECT_ID, zone=ZONE, instance=INSTANCE
    ).execute()


def external_ip(inst):
    for nic in inst.get("networkInterfaces", []):
        for cfg in nic.get("accessConfigs", []):
            if cfg.get("natIP"):
                return cfg["natIP"]
    return None


def boot_disk(inst):
    """The instance's boot disk as (name, deviceName)."""
    for disk in inst.get("disks", []):
        if disk.get("boot"):
            return disk["source"].rsplit("/", 1)[-1], disk.get("deviceName")
    raise RuntimeError("keine Boot-Disk gefunden")


def wait_for_op(operation, timeout=240):
    """Block until a zone/global operation finishes, or raise."""
    name = operation["name"]
    zonal = "zone" in operation
    deadline = time.time() + timeout
    while time.time() < deadline:
        if zonal:
            result = compute().zoneOperations().get(
                project=PROJECT_ID, zone=ZONE, operation=name).execute()
        else:
            result = compute().globalOperations().get(
                project=PROJECT_ID, operation=name).execute()
        if result.get("status") == "DONE":
            if "error" in result:
                raise RuntimeError(json.dumps(result["error"]))
            return result
        time.sleep(2)
    raise TimeoutError(f"Operation {name} dauert zu lange")


# --------------------------------------------------------------------------
# Steam query — asks the game itself, not just the hypervisor
# --------------------------------------------------------------------------

A2S_INFO = b"\xff\xff\xff\xffTSource Engine Query\x00"


def query_game(host, port, timeout=2.0):
    """Player count, or None when the game is not answering.

    The Compute API says RUNNING the moment the VM powers on, but the
    world still needs a couple of minutes to load. Asking the game port
    is the only way to know whether someone can actually join right now.

    Only A2S_INFO is worth asking: SotF does answer A2S_PLAYER, but with
    an empty name in every slot, so it reveals how many are connected and
    never who. Identity comes from the container log instead.
    """
    if not host:
        return None
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(timeout)
            sock.sendto(A2S_INFO, (host, port))
            data, _ = sock.recvfrom(4096)

            # Modern servers answer with a challenge first ('A'); echo it back.
            if len(data) > 4 and data[4:5] == b"A":
                sock.sendto(A2S_INFO + data[5:9], (host, port))
                data, _ = sock.recvfrom(4096)

            return parse_info(data)
    except (socket.timeout, OSError):
        return None


def parse_info(data):
    if len(data) < 6 or data[4:5] != b"I":
        return None
    pos = 6  # skip header + protocol byte

    def read_string():
        nonlocal pos
        end = data.index(b"\x00", pos)
        value = data[pos:end].decode("utf-8", "replace")
        pos = end + 1
        return value

    try:
        name = read_string()
        game_map = read_string()
        read_string()  # folder
        read_string()  # game
        pos += 2       # steam app id
        players = data[pos]
        max_players = data[pos + 1]
    except (ValueError, IndexError):
        return None

    return {
        "serverName": name,
        "map": game_map,
        "players": players,
        "maxPlayers": max_players,
    }


# --------------------------------------------------------------------------
# power
# --------------------------------------------------------------------------

def action_start():
    inst = instance()
    state = inst.get("status")
    if state == "RUNNING":
        return {"ok": True, "state": state, "message": "Laeuft schon."}, 200
    compute().instances().start(
        project=PROJECT_ID, zone=ZONE, instance=INSTANCE).execute()
    return {"ok": True, "state": "STAGING",
            "message": "Server faehrt hoch! In 3 Minuten koennt ihr joinen."}, 200


def action_stop(force):
    inst = instance()
    state = inst.get("status")
    if state == "TERMINATED":
        return {"ok": True, "state": state, "message": "Ist schon aus."}, 200

    # Refuse to pull the plug on a populated server unless asked twice.
    game = query_game(external_ip(inst), QUERY_PORT)
    if game and game["players"] > 0 and not force:
        return {
            "ok": False,
            "state": state,
            "players": game["players"],
            "message": f"{game['players']} Spieler online. "
                       f"Mit &force=1 trotzdem stoppen.",
        }, 409

    compute().instances().stop(
        project=PROJECT_ID, zone=ZONE, instance=INSTANCE).execute()
    return {"ok": True, "state": "STOPPING",
            "message": "Server faehrt herunter."}, 200


def describe_status(state, game, uptime):
    """One readable line, so the status link is useful in a browser too."""
    if state != "RUNNING":
        return {"TERMINATED": "Server ist aus.",
                "STAGING": "Server startet gerade.",
                "STOPPING": "Server faehrt herunter."}.get(state, f"Zustand: {state}")

    if game is None:
        return "VM laeuft, Welt laedt noch. Gleich koennt ihr joinen."

    who = f"{game['players']} von {game['maxPlayers']} Spielern online"
    if uptime is None:
        return f"Server laeuft. {who}."
    return f"Server laeuft seit {human_duration(uptime)}. {who}."


def human_duration(seconds):
    hours, minutes = seconds // 3600, (seconds % 3600) // 60
    return f"{hours} h {minutes} min" if hours else f"{minutes} min"


def action_status():
    inst = instance()
    state = inst.get("status")
    ip = external_ip(inst)
    game = query_game(ip, QUERY_PORT) if state == "RUNNING" else None

    uptime = None
    if state == "RUNNING" and inst.get("lastStartTimestamp"):
        # The API stamps an offset ("…-07:00"); dropping it and letting
        # mktime assume UTC silently added the offset to every reading.
        started = datetime.fromisoformat(inst["lastStartTimestamp"])
        uptime = max(0, int((datetime.now(timezone.utc) - started).total_seconds()))

    return {
        "ok": True,
        "state": state,
        "message": describe_status(state, game, uptime),
        "externalIp": ip,
        "lastStart": inst.get("lastStartTimestamp"),
        "lastStop": inst.get("lastStopTimestamp"),
        "uptimeSeconds": uptime,
        "gameReady": game is not None,
        "players": game["players"] if game else 0,
        "maxPlayers": game["maxPlayers"] if game else 0,
        "serverName": game["serverName"] if game else None,
    }, 200


# --------------------------------------------------------------------------
# backups — persistent disk snapshots
#
# Snapshots cover the whole disk, not just the savegame folder. Coarser
# than copying userdata/, but the function can do it entirely on its own:
# nothing has to be installed on the VM, and it works while the VM is off.
# --------------------------------------------------------------------------

def action_backup(label):
    inst = instance()
    disk_name, _ = boot_disk(inst)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    slug = re.sub(r"[^a-z0-9-]+", "-", (label or "").lower()).strip("-")[:20]
    name = f"{SNAPSHOT_PREFIX}-{stamp}" + (f"-{slug}" if slug else "")

    op = compute().disks().createSnapshot(
        project=PROJECT_ID, zone=ZONE, disk=disk_name,
        body={
            "name": name,
            "description": json.dumps({
                "label": label or "",
                "vmState": inst.get("status"),
                "created": stamp,
            }),
        },
    ).execute()

    # Snapshots keep filling in afterwards; waiting for READY here would
    # blow the request timeout on a 40 GB disk for no benefit.
    wait_for_op(op, timeout=90)

    return {"ok": True, "name": name,
            "message": f"Backup {name} wird erstellt."}, 200


def action_backups():
    # The Compute API rejects filter and orderBy together, so sort here.
    result = compute().snapshots().list(
        project=PROJECT_ID,
        filter=f'name:{SNAPSHOT_PREFIX}*',
        maxResults=100,
    ).execute()

    items = sorted(result.get("items", []),
                   key=lambda s: s.get("creationTimestamp", ""), reverse=True)

    backups = []
    for snap in items:
        meta = {}
        try:
            meta = json.loads(snap.get("description") or "{}")
        except json.JSONDecodeError:
            pass
        backups.append({
            "name": snap["name"],
            "createdAt": snap.get("creationTimestamp"),
            "status": snap.get("status"),
            "sizeGb": int(snap.get("storageBytes", 0)) // (1024 ** 3),
            "diskSizeGb": int(snap.get("diskSizeGb", 0)),
            "label": meta.get("label", ""),
        })

    if not backups:
        message = "Noch keine Backups."
    else:
        newest = backups[0]
        message = f"{len(backups)} Backups, neuestes: {newest['name']}."
    return {"ok": True, "backups": backups, "message": message}, 200


def action_restore(name):
    """Swap the boot disk for a fresh one built from a snapshot.

    The old disk is detached but never deleted, so a bad restore is
    reversible — at the cost of an unattached disk lingering until you
    remove it (about 1.60 EUR a month for 40 GB).
    """
    if not name:
        return {"ok": False, "message": "name= fehlt."}, 400

    inst = instance()
    if inst.get("status") != "TERMINATED":
        return {"ok": False, "state": inst.get("status"),
                "message": "Server muss erst aus sein. Zuerst action=stop."}, 409

    try:
        snap = compute().snapshots().get(
            project=PROJECT_ID, snapshot=name).execute()
    except Exception:  # noqa: BLE001 - a missing snapshot is a user error
        return {"ok": False, "message": f"Backup {name} gibt es nicht."}, 404
    if snap.get("status") != "READY":
        return {"ok": False,
                "message": f"Backup {name} ist noch nicht fertig "
                           f"({snap.get('status')})."}, 409

    old_disk, device_name = boot_disk(inst)
    new_disk = f"{INSTANCE}-{datetime.now(timezone.utc):%Y%m%d-%H%M%S}"

    op = compute().disks().insert(
        project=PROJECT_ID, zone=ZONE,
        body={
            "name": new_disk,
            "sourceSnapshot": snap["selfLink"],
            "sizeGb": snap.get("diskSizeGb"),
            "type": f"projects/{PROJECT_ID}/zones/{ZONE}/diskTypes/pd-balanced",
        },
    ).execute()
    wait_for_op(op)

    op = compute().instances().detachDisk(
        project=PROJECT_ID, zone=ZONE, instance=INSTANCE,
        deviceName=device_name).execute()
    wait_for_op(op)

    op = compute().instances().attachDisk(
        project=PROJECT_ID, zone=ZONE, instance=INSTANCE,
        body={
            "source": f"projects/{PROJECT_ID}/zones/{ZONE}/disks/{new_disk}",
            "boot": True,
            "autoDelete": False,
            "deviceName": device_name,
        },
    ).execute()
    wait_for_op(op)

    return {
        "ok": True,
        "restored": name,
        "newDisk": new_disk,
        "oldDisk": old_disk,
        "message": f"Backup {name} eingespielt. Alte Platte {old_disk} "
                   f"bleibt als Rueckfallnetz liegen.",
    }, 200


def action_delete_backup(name):
    if not name:
        return {"ok": False, "message": "name= fehlt."}, 400
    try:
        compute().snapshots().delete(project=PROJECT_ID, snapshot=name).execute()
    except Exception:  # noqa: BLE001
        return {"ok": False, "message": f"Backup {name} gibt es nicht."}, 404
    return {"ok": True, "deleted": name,
            "message": f"Backup {name} geloescht."}, 200


# --------------------------------------------------------------------------
# billing
#
# Uptime is read from Cloud Monitoring, not derived from log events.
# Google samples instance/uptime itself every minute, so the number is
# measured rather than inferred — an earlier version paired start/stop
# entries from the audit log and was badly wrong, because the idle
# shutdown terminates the VM from inside the guest and never produces a
# Compute API "stop" to pair against.
# --------------------------------------------------------------------------

RANGE_PRESETS = {
    "day": timedelta(days=1),
    "week": timedelta(days=7),
    "month": timedelta(days=30),
    "quarter": timedelta(days=91),
    "year": timedelta(days=365),
}

UPTIME_METRIC = "compute.googleapis.com/instance/uptime"


def measured_uptime(instance_id, start, end):
    """Seconds the instance actually ran, straight from Monitoring.

    instance/uptime is a DELTA metric in seconds; summing it per hour and
    adding the buckets gives the running time in the window.
    """
    request = api("monitoring", "v3").projects().timeSeries().list(
        name=f"projects/{PROJECT_ID}",
        filter=(f'metric.type="{UPTIME_METRIC}" AND '
                f'resource.labels.instance_id="{instance_id}"'),
        interval_startTime=start.isoformat().replace("+00:00", "Z"),
        interval_endTime=end.isoformat().replace("+00:00", "Z"),
        aggregation_alignmentPeriod="3600s",
        aggregation_perSeriesAligner="ALIGN_SUM",
        view="FULL",
    )

    total = 0.0
    while request is not None:
        result = request.execute()
        for series in result.get("timeSeries", []):
            for point in series.get("points", []):
                value = point.get("value", {})
                total += float(value.get("doubleValue")
                               or value.get("int64Value") or 0)
        request = api("monitoring", "v3").projects().timeSeries().list_next(
            request, result)

    return int(total)


# --------------------------------------------------------------------------
# per-player split
#
# Kevke's decomposition: your bill is the sum of one bucket per crowd
# size. Time you spent alone costs full rate, time with one other costs
# half, with two others a third:
#
#     S = A + B + C + …          A = M/L·G   B = M/L·G/2   C = M/L·G/3
#
# Since G/L is just the hourly rate, a second spent with n players on the
# server costs each of them rate/n — so the shares add up to exactly what
# the populated time cost, and idle time stays unattributed.
#
# Identity comes from the container log, which names both the Steam ID
# and the display name on every connect and disconnect. The Steam query
# port cannot supply it: SotF answers A2S_PLAYER with an empty name for
# every slot, so it only ever reveals how many are on, never who.
#
# The log reaches us through the Ops Agent, which ships Docker output to
# Cloud Logging. Join and leave timestamps are therefore exact — no
# sampling, no interpolation.
# --------------------------------------------------------------------------

RE_JOIN = re.compile(
    r"Steam auth successful for client \d+ with steam id (\d{17}), username (.+?)\s*$")
RE_LEAVE = re.compile(
    r"Unregistering client \d+ with steam id (\d{17})")


def player_events(start, end):
    """(timestamp, steamId, name, "join"|"leave") from the container log."""
    filter_str = (
        f'resource.type="gce_instance" '
        f'timestamp>="{start.isoformat().replace("+00:00", "Z")}" '
        f'timestamp<="{end.isoformat().replace("+00:00", "Z")}" '
        f'("Steam auth successful" OR "Unregistering client")'
    )
    body = {
        "resourceNames": [f"projects/{PROJECT_ID}"],
        "filter": filter_str,
        "orderBy": "timestamp asc",
        "pageSize": 1000,
    }

    events = []
    page_token = None
    while True:
        if page_token:
            body["pageToken"] = page_token
        result = api("logging", "v2").entries().list(body=body).execute()
        for entry in result.get("entries", []):
            text = entry.get("textPayload") or ""
            if not text:
                payload = entry.get("jsonPayload") or {}
                text = str(payload.get("message") or payload.get("log") or "")
            moment = datetime.fromisoformat(
                entry["timestamp"].replace("Z", "+00:00"))

            match = RE_JOIN.search(text)
            if match:
                events.append((moment, match.group(1), match.group(2).strip(), "join"))
                continue
            match = RE_LEAVE.search(text)
            if match:
                events.append((moment, match.group(1), None, "leave"))
        page_token = result.get("nextPageToken")
        if not page_token:
            break

    events.sort(key=lambda e: e[0])
    return events


def split_by_player(events, start, end, rate_per_second):
    """Walk the timeline, charging each stretch by how many were on."""
    names = {}
    for _, steam_id, name, _ in events:
        if name:
            names[steam_id] = name

    per_player = {}
    counted = 0.0
    online = set()
    previous = start

    def charge(until):
        nonlocal counted
        span = (until - previous).total_seconds()
        if span <= 0 or not online:
            return
        counted += span
        crowd = len(online)
        share = span * rate_per_second / crowd
        for steam_id in online:
            slot = per_player.setdefault(
                steam_id, {"seconds": 0.0, "eur": 0.0, "buckets": {}})
            slot["seconds"] += span
            slot["eur"] += share
            key = str(crowd)
            slot["buckets"][key] = slot["buckets"].get(key, 0.0) + span

    for moment, steam_id, _, kind in events:
        moment = max(min(moment, end), start)
        charge(moment)
        previous = moment
        if kind == "join":
            online.add(steam_id)
        else:
            online.discard(steam_id)

    # Anyone still connected at the end of the window keeps counting to it.
    charge(end)

    players = [
        {
            "steamId": steam_id,
            "name": names.get(steam_id, steam_id),
            "seconds": int(slot["seconds"]),
            "eur": round(slot["eur"], 2),
            # seconds spent while n players were on, keyed by n
            "buckets": {k: int(v) for k, v in sorted(slot["buckets"].items())},
        }
        for steam_id, slot in per_player.items()
    ]
    players.sort(key=lambda p: p["eur"], reverse=True)
    return players, int(counted)


def action_billing(range_key, custom_from, custom_to):
    end = datetime.now(timezone.utc)
    if range_key == "custom":
        if not custom_from or not custom_to:
            return {"ok": False, "message": "from= und to= noetig."}, 400
        start = datetime.fromisoformat(custom_from.replace("Z", "+00:00"))
        end = datetime.fromisoformat(custom_to.replace("Z", "+00:00"))
    elif range_key in RANGE_PRESETS:
        start = end - RANGE_PRESETS[range_key]
    else:
        return {"ok": False,
                "message": f"range={range_key} kenne ich nicht."}, 400

    inst = instance()
    try:
        seconds = measured_uptime(inst["id"], start, end)
    except Exception as exc:  # noqa: BLE001 - surfaced so the cause is visible
        return {"ok": False, "source": "monitoring",
                "message": f"Laufzeit nicht abrufbar: {exc}"}, 502

    rate_per_second = HOURLY_RATE / 3600
    cost = round(seconds * rate_per_second, 2)

    players, attributed = split_by_player(
        player_events(start, end), start, end, rate_per_second)
    idle = max(0, seconds - attributed)

    summary = f"{human_duration(seconds)} gemessene Laufzeit, rund {cost:.2f} EUR."
    if players:
        summary += " " + ", ".join(f"{p['name']}: {p['eur']:.2f}" for p in players)

    return {
        "ok": True,
        "range": range_key,
        "from": start.isoformat().replace("+00:00", "Z"),
        "to": end.isoformat().replace("+00:00", "Z"),
        "uptimeSeconds": seconds,
        "hourlyRateEur": HOURLY_RATE,
        "totalEur": cost,
        "source": "cloud-monitoring",
        "perPlayer": players,
        "unattributedSeconds": idle,
        "unattributedEur": round(idle * rate_per_second, 2),
        "message": summary,
    }, 200


# --------------------------------------------------------------------------

@functions_framework.http
def sotf_control(request):
    token = request.args.get("token", "")
    if not hmac.compare_digest(token, TOKEN):
        return ("noe_", 403, {"Content-Type": "text/plain; charset=utf-8"})

    action = request.args.get("action", "status").lower()
    force = request.args.get("force") in ("1", "true", "yes")
    name = request.args.get("name")

    try:
        if action == "start":
            payload, code = action_start()
        elif action == "stop":
            payload, code = action_stop(force)
        elif action == "status":
            payload, code = action_status()
        elif action == "backup":
            payload, code = action_backup(request.args.get("label"))
        elif action == "backups":
            payload, code = action_backups()
        elif action == "restore":
            payload, code = action_restore(name)
        elif action == "delete_backup":
            payload, code = action_delete_backup(name)
        elif action == "billing":
            payload, code = action_billing(
                request.args.get("range", "month"),
                request.args.get("from"), request.args.get("to"))
        else:
            payload, code = {"ok": False,
                             "message": f"Unbekannte action: {action}"}, 400
    except Exception as exc:  # noqa: BLE001 - the caller gets the reason
        payload, code = {"ok": False, "message": f"Fehler: {exc}"}, 500

    # A browser gets a readable line; the app asks for JSON via Accept.
    wants_json = "application/json" in request.headers.get("Accept", "")
    if wants_json or request.args.get("format") == "json":
        return (json.dumps(payload), code,
                {"Content-Type": "application/json; charset=utf-8"})
    return (payload.get("message", json.dumps(payload)), code,
            {"Content-Type": "text/plain; charset=utf-8"})
