#!/usr/bin/env bash
# Shuts the VM down when the Sons of the Forest server has been idle for
# SOTF_IDLE_TIMEOUT_SECONDS.
#
# Idle detection: the SOTF dedicated server prints
#   "Set target framerate to <n>"
# once per second while nobody is connected. As soon as a player joins,
# other log lines appear. So: whenever `docker logs --tail 1` still ends
# on that line, the server is idle. We count how long that has been true
# and shut the VM down once the threshold is crossed.
#
# Flags / env:
#   --dry-run                    log what would happen, do NOT poweroff
#   SOTF_IDLE_TIMEOUT_SECONDS    idle threshold (default 900 = 15 min)
#   SOTF_CHECK_INTERVAL_SECONDS  poll interval  (default 30)
#   SOTF_CONTAINER_NAME          container to watch (default sotf-server)

set -euo pipefail

IDLE_TIMEOUT="${SOTF_IDLE_TIMEOUT_SECONDS:-900}"
CHECK_INTERVAL="${SOTF_CHECK_INTERVAL_SECONDS:-30}"
CONTAINER_NAME="${SOTF_CONTAINER_NAME:-sotf-server}"
IDLE_MARKER='Set target framerate'

DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help)
      sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '[sotf-idle-shutdown] %s\n' "$*"; }

log "start: container=$CONTAINER_NAME idle_timeout=${IDLE_TIMEOUT}s interval=${CHECK_INTERVAL}s dry_run=$DRY_RUN"

idle_seconds=0
while true; do
  if ! docker inspect -f '{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null | grep -q true; then
    log "container $CONTAINER_NAME not running; resetting idle counter"
    idle_seconds=0
    sleep "$CHECK_INTERVAL"
    continue
  fi

  last_line="$(docker logs --tail 1 "$CONTAINER_NAME" 2>&1 || true)"

  if [[ "$last_line" == *"$IDLE_MARKER"* ]]; then
    idle_seconds=$(( idle_seconds + CHECK_INTERVAL ))
    log "idle for ${idle_seconds}s / ${IDLE_TIMEOUT}s"
  else
    if (( idle_seconds > 0 )); then
      log "activity detected, resetting idle counter (last line: ${last_line:0:80})"
    fi
    idle_seconds=0
  fi

  if (( idle_seconds >= IDLE_TIMEOUT )); then
    if (( DRY_RUN == 1 )); then
      log "DRY RUN: would run /sbin/shutdown -h now"
      exit 0
    fi
    log "idle threshold reached, powering off"
    /sbin/shutdown -h now
    exit 0
  fi

  sleep "$CHECK_INTERVAL"
done
