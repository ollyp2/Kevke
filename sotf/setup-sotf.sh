#!/usr/bin/env bash
# Idempotent bootstrap for a fresh Debian/Ubuntu VM that will host the
# Sons of the Forest dedicated server.
#
# Safe to re-run:
#   - Package installs use `install` (no-op if already installed).
#   - Files are only rewritten if content differs.
#   - `docker compose up -d` is a converge, not a wipe.
#   - The userdata bind mount (savegames) is NEVER touched.
#
# Run as root on the VM:  sudo bash setup-sotf.sh
#
# Assumes:
#   - GCP firewall + static IP are already handled from your workstation
#     (see gcp/firewall.sh and gcp/static-ip.sh).
#   - You have edited $SOTF_DIR/.env before starting the container the
#     first time (or the compose file will refuse to start).

set -euo pipefail

SOTF_USER="${SOTF_USER:-kradtke79}"
SOTF_DIR="${SOTF_DIR:-/home/${SOTF_USER}/sotf}"
REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"

log() { printf '\n== %s ==\n' "$*"; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    echo "run as root (sudo bash $0)" >&2
    exit 1
  fi
}

install_docker_and_compose_v2() {
  log "docker + compose v2"
  if ! command -v docker >/dev/null; then
    # Debian/Ubuntu convenience installer, upstream-signed.
    curl -fsSL https://get.docker.com | sh
  fi
  # docker-compose v1 (`docker-compose`, with hyphen) is the one that
  # crashes on recreate with `KeyError: 'ContainerConfig'`. v2 ships as
  # a docker CLI plugin (`docker compose`, space) and doesn't have that
  # bug. Installing v2 in addition to v1 is fine — they coexist.
  apt-get update -y
  apt-get install -y docker-compose-plugin
  systemctl enable --now docker
  usermod -aG docker "$SOTF_USER" || true
}

place_compose_and_env() {
  log "compose file + env at $SOTF_DIR"
  install -d -o "$SOTF_USER" -g "$SOTF_USER" "$SOTF_DIR" "$SOTF_DIR/userdata"
  install -o "$SOTF_USER" -g "$SOTF_USER" -m 0644 \
    "$REPO_ROOT/docker-compose.yml" "$SOTF_DIR/docker-compose.yml"
  if [[ ! -f "$SOTF_DIR/.env" ]]; then
    install -o "$SOTF_USER" -g "$SOTF_USER" -m 0600 \
      "$REPO_ROOT/.env.example" "$SOTF_DIR/.env"
    echo "wrote $SOTF_DIR/.env — EDIT it before starting the server"
  else
    echo "$SOTF_DIR/.env exists, leaving as-is"
  fi
}

place_idle_shutdown() {
  log "idle-shutdown script + service"
  install -m 0755 \
    "$REPO_ROOT/idle-shutdown/sotf-idle-shutdown.sh" \
    /usr/local/bin/sotf-idle-shutdown.sh
  install -m 0644 \
    "$REPO_ROOT/idle-shutdown/sotf-idle-shutdown.service" \
    /etc/systemd/system/sotf-idle-shutdown.service
  systemctl daemon-reload
  systemctl enable --now sotf-idle-shutdown.service
}

remove_ops_agent_if_present() {
  log "google-cloud-ops-agent (uninstall if present)"
  # The Ops Agent floods journald with metric-write 403s when the
  # Monitoring API is disabled on the project. We're not using Cloud
  # Monitoring for this hobby server, so remove the agent outright —
  # cleanest fix, no config drift.
  if dpkg -s google-cloud-ops-agent >/dev/null 2>&1; then
    apt-get purge -y google-cloud-ops-agent
    rm -rf /etc/google-cloud-ops-agent /var/log/google-cloud-ops-agent
  else
    echo "not installed, skipping"
  fi
}

start_or_converge_container() {
  log "docker compose up -d"
  if [[ ! -s "$SOTF_DIR/.env" ]] || grep -q 'changeMe' "$SOTF_DIR/.env"; then
    echo "!! $SOTF_DIR/.env still has placeholder values."
    echo "   Edit it and re-run \`docker compose up -d\` yourself."
    return 0
  fi
  # Prefer v2. If someone re-runs on the legacy setup where only v1 is
  # present, do the down-then-up dance to dodge the ContainerConfig bug.
  if docker compose version >/dev/null 2>&1; then
    ( cd "$SOTF_DIR" && docker compose pull && docker compose up -d )
  else
    ( cd "$SOTF_DIR" && docker-compose down && docker-compose pull && docker-compose up -d )
  fi
}

main() {
  require_root
  install_docker_and_compose_v2
  place_compose_and_env
  place_idle_shutdown
  remove_ops_agent_if_present
  start_or_converge_container
  log "done"
  echo "next steps:"
  echo "  1. edit $SOTF_DIR/.env (SOTF_SERVERNAME, SOTF_SERVERPASSWORD)"
  echo "  2. (cd $SOTF_DIR && docker compose up -d)"
  echo "  3. docker logs -f sotf-server   # wait for 'Set target framerate'"
}

main "$@"
