#!/usr/bin/env bash
# Shuts the VM down when the Sons of the Forest server has been idle for
# SOTF_IDLE_TIMEOUT_SECONDS.
#
# Idle detection: CPU usage of the container. An empty SOTF dedicated
# server sits at ~1-5 % CPU (just periodic autosaves), a populated one
# is well above 20 %. Log-parsing was tried first (matching a specific
# "Set target framerate" transition) and turned out to be fragile — the
# marker line does not always fire, and upstream image changes can
# silently break the string match. `docker stats` is language-agnostic
# and cannot be fooled by log-format drift.
#
# Flags / env:
#   --dry-run                    log what would happen, do NOT poweroff
#   SOTF_IDLE_TIMEOUT_SECONDS    idle threshold (default 900 = 15 min)
#   SOTF_CHECK_INTERVAL_SECONDS  poll interval  (default 30)
#   SOTF_CPU_THRESHOLD_PCT       "idle" if CPU% < this (default 10)
#   SOTF_CONTAINER_NAME          container to watch (default sotf-server)

set -euo pipefail

IDLE_TIMEOUT="${SOTF_IDLE_TIMEOUT_SECONDS:-900}"
CHECK_INTERVAL="${SOTF_CHECK_INTERVAL_SECONDS:-30}"
CPU_THRESHOLD="${SOTF_CPU_THRESHOLD_PCT:-10}"
CONTAINER_NAME="${SOTF_CONTAINER_NAME:-sotf-server}"

DRY_RUN=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "unknown arg: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '[sotf-idle-shutdown] %s\n' "$*"; }

container_cpu_pct() {
  # `docker stats --no-stream` prints one sample. Strip the trailing '%'.
  # Empty output (container gone) → return -1 so the caller can react.
  local raw
  raw="$(docker stats --no-stream --format '{{.CPUPerc}}' "$CONTAINER_NAME" 2>/dev/null || true)"
  raw="${raw%\%}"
  if [[ -z "$raw" ]]; then
    echo "-1"
  else
    echo "$raw"
  fi
}

log "start: container=$CONTAINER_NAME idle_timeout=${IDLE_TIMEOUT}s interval=${CHECK_INTERVAL}s cpu_threshold=${CPU_THRESHOLD}% dry_run=$DRY_RUN"

idle_seconds=0
while true; do
  cpu="$(container_cpu_pct)"

  if [[ "$cpu" == "-1" ]]; then
    log "container $CONTAINER_NAME not running; resetting idle counter"
    idle_seconds=0
    sleep "$CHECK_INTERVAL"
    continue
  fi

  # bash cannot compare floats — awk does.
  is_idle="$(awk -v c="$cpu" -v t="$CPU_THRESHOLD" 'BEGIN{ print (c+0 < t+0) ? 1 : 0 }')"

  if [[ "$is_idle" == "1" ]]; then
    idle_seconds=$(( idle_seconds + CHECK_INTERVAL ))
    log "cpu=${cpu}% (< ${CPU_THRESHOLD}%) idle for ${idle_seconds}s / ${IDLE_TIMEOUT}s"
  else
    if (( idle_seconds > 0 )); then
      log "cpu=${cpu}% (>= ${CPU_THRESHOLD}%) activity, resetting idle counter"
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
