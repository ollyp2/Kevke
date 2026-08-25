#!/usr/bin/env bash
# Write Kevke's chosen game rules into dedicatedserver.cfg.
#
#   sudo bash apply-game-settings.sh            # show what would change
#   sudo bash apply-game-settings.sh --write    # actually write it
#
# Only the server config is touched. Nothing under Saves/ is read or
# written — the savegame is the one thing here that cannot be rebuilt.
#
# Editing JSON with sed is how you end up with a config the server
# silently refuses to load, so the actual edit runs through Python's
# json module: parse, set the keys by name, dump. A key that does not
# already exist in the file is reported instead of added, because a name
# this server does not know is a typo, not a feature.

set -euo pipefail

CFG="${SOTF_CFG:-/home/kradtke79/sotf/userdata/dedicatedserver.cfg}"
CONTAINER="${SOTF_CONTAINER:-sotf-server}"

WRITE=0
for arg in "$@"; do
  case "$arg" in
    --write) WRITE=1 ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "unbekanntes Argument: $arg" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$CFG" ]]; then
  echo "Config nicht gefunden: $CFG" >&2
  exit 1
fi

# The timestamp is built here rather than left as something to fill in.
# A placeholder in a line meant to be pasted has cost this project a
# savegame once already.
BACKUP="${CFG}.bak.$(date +%Y%m%d-%H%M%S)"

WRITE="$WRITE" CFG="$CFG" BACKUP="$BACKUP" python3 <<'PY'
import json
import os
import shutil
import sys

cfg_path = os.environ["CFG"]
backup_path = os.environ["BACKUP"]
write = os.environ["WRITE"] == "1"

# --- what Kevke picked, 2026-08-25 -------------------------------------
# GameSettings applies to any world, including one already in progress.
GAME_SETTINGS = {
    "Structure.Damage": False,      # Bauten nehmen keinen Schaden
    "Gameplay.TreeRegrowth": True,  # Baeume wachsen nach
}

# CustomGameModeSettings needs GameMode "Custom" — and, going by every
# source and by this server's own behaviour, binds when a world is
# created. Written anyway: it is correct, and it is what a new world
# would start from.
CUSTOM = {
    "GameSetting.Multiplayer.Cheats": True,
    "GameSetting.Multiplayer.PvpDamage": "Off",

    "GameSetting.Vail.EnemySpawn": True,
    "GameSetting.Vail.EnemyHealth": "High",
    "GameSetting.Vail.EnemyDamage": "High",
    "GameSetting.Vail.EnemyArmour": "Normal",
    "GameSetting.Vail.EnemyAggression": "High",
    "GameSetting.Vail.AnimalSpawnRate": "Normal",
    "GameSetting.Vail.EnemySearchParties": "Normal",

    "GameSetting.Environment.StartingSeason": "Spring",
    "GameSetting.Environment.SeasonLength": "Long",
    "GameSetting.Environment.DayLength": "Default",
    "GameSetting.Environment.PrecipitationFrequency": "Default",

    "GameSetting.Survival.ConsumableEffects": "Normal",
    "GameSetting.Survival.PlayerStatsDamage": "Normal",
    "GameSetting.Survival.ColdPenalties": "Normal",
    "GameSetting.Survival.StatRegenerationPenalty": "Normal",
    "GameSetting.Survival.ReducedFoodInContainers": True,
    "GameSetting.Survival.SingleUseContainers": False,
    "GameSetting.Survival.BuildingResistance": "Normal",
    "GameSetting.Survival.CreativeMode": False,
    "GameSetting.Survival.PlayersImmortalMode": False,

    "GameSetting.Gameplay.RespawnContainerItems": True,
}

TOP_LEVEL = {
    "GameMode": "Custom",   # ohne das wird der Custom-Block ignoriert
}
# ----------------------------------------------------------------------

with open(cfg_path, encoding="utf-8") as handle:
    cfg = json.load(handle)

changes, unknown = [], []


def apply(block, wanted, label):
    for key, value in wanted.items():
        if key not in block:
            unknown.append(f"{label}.{key}")
            continue
        if block[key] != value:
            changes.append((f"{label}.{key}", block[key], value))
            block[key] = value


apply(cfg, TOP_LEVEL, "")
apply(cfg.setdefault("GameSettings", {}), GAME_SETTINGS, "GameSettings")
apply(cfg.setdefault("CustomGameModeSettings", {}), CUSTOM, "CustomGameModeSettings")

if unknown:
    print("Diese Schluessel kennt deine Serverversion nicht:")
    for key in unknown:
        print(f"  {key}")
    print("Nichts geschrieben — erst klaeren, dann nochmal.")
    sys.exit(1)

if not changes:
    print("Nichts zu tun, die Config steht schon so.")
    sys.exit(0)

width = max(len(name) for name, _, _ in changes)
print(f"{len(changes)} Aenderung(en):")
for name, old, new in changes:
    print(f"  {name.ljust(width)}  {json.dumps(old)} -> {json.dumps(new)}")

if not write:
    print()
    print("Das war eine Vorschau. Zum Schreiben nochmal mit --write.")
    sys.exit(0)

shutil.copy2(cfg_path, backup_path)

# Write beside the target and move into place, so an interrupted run
# cannot leave a half-written config the server would choke on.
temp_path = cfg_path + ".new"
with open(temp_path, "w", encoding="utf-8") as handle:
    json.dump(cfg, handle, indent=4, ensure_ascii=False)
    handle.write("\n")

with open(temp_path, encoding="utf-8") as handle:
    json.load(handle)  # refuse to install anything that will not parse

os.replace(temp_path, cfg_path)
print()
print(f"Geschrieben. Alte Fassung liegt unter:\n  {backup_path}")
PY

if [[ "$WRITE" == "1" ]]; then
  cat <<EOF

Der Server liest die Config beim Start. Damit sie greift:

  sudo docker restart ${CONTAINER}

Das nimmt den Server fuer ein bis zwei Minuten offline — nicht machen,
solange jemand drauf spielt.
EOF
fi
