"""sotf-control — one function, one token, everything via URL.

Extends the existing sotf-start-trigger pattern instead of replacing it:
same query-string token, same plain-text answers, just more actions.

    ?token=…&action=start    start the VM
    ?token=…&action=stop     stop the VM
    ?token=…&action=status   is it up, is the world loaded, who is on

Every action works from a browser, from curl, and from a button in the
app — there is nothing to install on the VM for these three.

Env vars (set at deploy time):
    TOKEN        shared secret, compared against ?token=
    PROJECT_ID   GCP project
    ZONE         e.g. europe-west3-a
    INSTANCE     e.g. sotf-server
    QUERY_PORT   Steam query port, default 27016
"""

import hmac
import json
import os
import socket
from datetime import datetime, timezone

import functions_framework
from googleapiclient import discovery

TOKEN = os.environ["TOKEN"]
PROJECT_ID = os.environ["PROJECT_ID"]
ZONE = os.environ.get("ZONE", "europe-west3-a")
INSTANCE = os.environ.get("INSTANCE", "sotf-server")
QUERY_PORT = int(os.environ.get("QUERY_PORT", "27016"))

_compute = None


def compute():
    global _compute
    if _compute is None:
        _compute = discovery.build("compute", "v1", cache_discovery=False)
    return _compute


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


# --------------------------------------------------------------------------
# Steam query — asks the game itself, not just the hypervisor
# --------------------------------------------------------------------------

A2S_INFO = b"\xff\xff\xff\xffTSource Engine Query\x00"


def query_game(host, port, timeout=2.0):
    """Return player counts, or None when the game is not answering.

    The Compute API says RUNNING the moment the VM powers on, but the world
    still needs a couple of minutes to load. Asking the game port is the
    only way to know whether someone can actually join right now.
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
# actions
# --------------------------------------------------------------------------

def action_start():
    inst = instance()
    state = inst.get("status")
    if state == "RUNNING":
        return {"ok": True, "state": state,
                "message": "Laeuft schon."}, 200
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


def describe(state, game, uptime):
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
    hours, minutes = uptime // 3600, (uptime % 3600) // 60
    seit = f"{hours} h {minutes} min" if hours else f"{minutes} min"
    return f"Server laeuft seit {seit}. {who}."


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
        "message": describe(state, game, uptime),
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

@functions_framework.http
def sotf_control(request):
    token = request.args.get("token", "")
    if not hmac.compare_digest(token, TOKEN):
        return ("noe_", 403, {"Content-Type": "text/plain; charset=utf-8"})

    action = request.args.get("action", "status").lower()
    force = request.args.get("force") in ("1", "true", "yes")

    try:
        if action == "start":
            payload, code = action_start()
        elif action == "stop":
            payload, code = action_stop(force)
        elif action == "status":
            payload, code = action_status()
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
