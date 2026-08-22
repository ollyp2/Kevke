#!/usr/bin/env bash
# Install the agent on the game VM. Idempotent — safe to re-run.
#
#   sudo bash install.sh
#
# Needs a Firestore-capable credential. The VM's default service account has
# restricted scopes, so either widen them (requires a VM stop/start) or drop
# a service-account key at /etc/sotf-agent-sa.json before running this.

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "run as root: sudo bash $0" >&2
  exit 1
fi

SRC="$(cd "$(dirname "$0")" && pwd)"
DEST=/opt/sotf-agent

PROJECT_ID="${PROJECT_ID:-gen-lang-client-0146272192}"
SERVER_ID="${SERVER_ID:-sotf-main}"
CONTAINER="${CONTAINER:-sotf-server}"
SOTF_DIR="${SOTF_DIR:-/home/kradtke79/sotf}"

echo "== python venv =="
apt-get update -y
apt-get install -y python3-venv python3-pip
install -d "$DEST"
if [[ ! -x "$DEST/venv/bin/python" ]]; then
  python3 -m venv "$DEST/venv"
fi
"$DEST/venv/bin/pip" install --quiet --upgrade pip google-cloud-firestore

echo "== agent script =="
install -m 0755 "$SRC/sotf-agent.py" "$DEST/sotf-agent.py"

echo "== environment =="
if [[ ! -f /etc/sotf-agent.env ]]; then
  cat > /etc/sotf-agent.env <<EOF
SOTF_PROJECT_ID=${PROJECT_ID}
SOTF_SERVER_ID=${SERVER_ID}
SOTF_CONTAINER=${CONTAINER}
SOTF_DIR=${SOTF_DIR}
SOTF_SAMPLE_SECONDS=30
SOTF_JOB_POLL_SECONDS=10
SOTF_METRIC_RETENTION_DAYS=30
EOF
  # Only set when a key file is actually present; otherwise the agent uses
  # the VM's own service account.
  if [[ -f /etc/sotf-agent-sa.json ]]; then
    echo "GOOGLE_APPLICATION_CREDENTIALS=/etc/sotf-agent-sa.json" >> /etc/sotf-agent.env
  fi
  chmod 0600 /etc/sotf-agent.env
  echo "wrote /etc/sotf-agent.env"
else
  echo "/etc/sotf-agent.env exists, leaving as-is"
fi

echo "== backup dir =="
install -d -o 1000 -g 1000 "${SOTF_DIR}/backups"

echo "== systemd unit =="
install -m 0644 "$SRC/sotf-agent.service" /etc/systemd/system/sotf-agent.service
systemctl daemon-reload
systemctl enable --now sotf-agent.service

echo
echo "== status =="
systemctl is-active sotf-agent.service
journalctl -u sotf-agent -n 20 --no-pager
