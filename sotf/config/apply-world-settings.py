#!/usr/bin/env python3
"""Write the game rules into the savegame, where this world actually reads them.

    sudo python3 apply-world-settings.py            # zeigt nur, was sich aendert
    sudo python3 apply-world-settings.py --write    # schreibt wirklich

Why the savegame and not dedicatedserver.cfg: measured on the live
server, the two config blocks behave differently. GameSettings applies
to any world — Structure.Damage works. Everything under
CustomGameModeSettings is read and then ignored, because the world keeps
its own copy of the rules and that copy wins.

That copy lives in GameSetupSaveData.json inside SaveData.zip, as JSON
encoded into a string inside JSON:

    {"Version":"0.0.0","Data":{"GameSetup":"{\\"_settings\\":[ ... ]}"}}

and it currently says Mode "Hard" — which is why a config saying Custom
changes nothing.

The zip is rebuilt entry by entry so every one of the other 37 files
comes through byte for byte; only GameSetupSaveData.json is replaced.
"""

import argparse
import datetime
import json
import os
import shutil
import sys
import zipfile

SETUP_ENTRY = "GameSetupSaveData.json"

# --- what Kevke picked, 2026-08-25 -------------------------------------
MODE = "Custom"

# Booleans carry BoolValue and no SettingType, strings carry
# SettingType 3 — both mirror how the world already writes its own
# entries, so nothing here invents a shape the game has not used itself.
BOOLS = {
    "GameSetting.Vail.EnemySpawn": True,
    "GameSetting.Multiplayer.Cheats": True,
    "GameSetting.Survival.ReducedFoodInContainers": True,
    "GameSetting.Survival.SingleUseContainers": False,
    "GameSetting.Survival.CreativeMode": False,
    "GameSetting.Survival.PlayersImmortalMode": False,
    "GameSetting.Gameplay.RespawnContainerItems": True,
}

STRINGS = {
    "GameSetting.Multiplayer.PvpDamage": "Off",

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
    "GameSetting.Survival.BuildingResistance": "Normal",
}
# ----------------------------------------------------------------------

DEFAULT_SAVE = ("/home/kradtke79/sotf/userdata/Saves/DedicatedServer"
                "/Multiplayer/0000000001/SaveData.zip")


def load_setup(raw):
    """The outer wrapper, and the settings list buried in it as a string."""
    outer = json.loads(raw)
    inner = json.loads(outer["Data"]["GameSetup"])
    return outer, inner


def dump_setup(outer, inner):
    outer["Data"]["GameSetup"] = json.dumps(inner, separators=(",", ":"))
    return json.dumps(outer, separators=(",", ":")).encode("utf-8")


def upsert(settings, name, payload):
    """Replace the entry called `name`, or append it. Returns (old, new)."""
    for entry in settings:
        if entry.get("Name") == name:
            before = dict(entry)
            entry.clear()
            entry["Name"] = name
            entry.update(payload)
            return before, entry
    entry = {"Name": name}
    entry.update(payload)
    settings.append(entry)
    return None, entry


def value_of(entry):
    if entry is None:
        return None
    return entry.get("StringValue", entry.get("BoolValue"))


def plan(inner):
    """Apply every wanted value; report what moved. UID is never touched."""
    settings = inner["_settings"]
    changes, added = [], []

    wanted = [("Mode", {"SettingType": 3, "StringValue": MODE})]
    wanted += [(k, {"SettingType": 3, "StringValue": v})
               for k, v in STRINGS.items()]
    wanted += [(k, {"BoolValue": v}) for k, v in BOOLS.items()]

    for name, payload in wanted:
        before, after = upsert(settings, name, payload)
        if before is None:
            added.append((name, value_of(after)))
        elif value_of(before) != value_of(after):
            changes.append((name, value_of(before), value_of(after)))

    return changes, added


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("save", nargs="?", default=DEFAULT_SAVE)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()

    if not os.path.isfile(args.save):
        sys.exit(f"Spielstand nicht gefunden: {args.save}")

    with zipfile.ZipFile(args.save) as archive:
        names = archive.namelist()
        if SETUP_ENTRY not in names:
            sys.exit(f"{SETUP_ENTRY} steckt nicht in diesem Zip.")
        outer, inner = load_setup(archive.read(SETUP_ENTRY))

    mode_before = next(
        (e.get("StringValue") for e in inner["_settings"]
         if e.get("Name") == "Mode"), "?")
    changes, added = plan(inner)

    print(f"Modus der Welt: {mode_before}")
    if changes:
        width = max(len(n) for n, _, _ in changes)
        print(f"\n{len(changes)} geaendert:")
        for name, old, new in changes:
            print(f"  {name.ljust(width)}  {json.dumps(old)} -> {json.dumps(new)}")
    if added:
        width = max(len(n) for n, _ in added)
        print(f"\n{len(added)} neu hinzugefuegt:")
        for name, new in added:
            print(f"  {name.ljust(width)}  {json.dumps(new)}")
    if not changes and not added:
        print("\nNichts zu tun, der Spielstand steht schon so.")
        return

    payload = dump_setup(outer, inner)
    load_setup(payload)  # refuse to install anything that will not parse back

    if not args.write:
        print("\nDas war eine Vorschau. Zum Schreiben nochmal mit --write.")
        return

    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    backup = f"{args.save}.bak.{stamp}"
    shutil.copy2(args.save, backup)

    # Build the new archive beside the old one and move it into place, so
    # an interrupted run cannot leave a truncated savegame behind.
    temp = args.save + ".new"
    with zipfile.ZipFile(args.save) as source, \
            zipfile.ZipFile(temp, "w") as target:
        for info in source.infolist():
            data = payload if info.filename == SETUP_ENTRY else source.read(info)
            target.writestr(info, data, compress_type=info.compress_type)

    with zipfile.ZipFile(temp) as check:
        if check.testzip() is not None:
            os.unlink(temp)
            sys.exit("Neues Zip ist beschaedigt. Nichts angefasst.")
        missing = set(names) - set(check.namelist())
        if missing:
            os.unlink(temp)
            sys.exit(f"Im neuen Zip fehlen Dateien: {sorted(missing)}")
        load_setup(check.read(SETUP_ENTRY))

    shutil.copystat(args.save, temp)
    os.replace(temp, args.save)
    print(f"\nGeschrieben. Alte Fassung liegt unter:\n  {backup}")


if __name__ == "__main__":
    main()
