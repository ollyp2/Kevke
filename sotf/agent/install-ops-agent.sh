#!/usr/bin/env bash
# Ship the game container's log to Cloud Logging.
#
#   sudo bash install-ops-agent.sh
#
# The billing split needs to know who was connected when, and only the
# container log carries that: every join and leave line names the Steam
# ID and the display name. The Steam query port cannot supply it — SotF
# answers A2S_PLAYER with an empty name in every slot.
#
# This is Google's own agent, not a custom daemon. It was removed early
# on because it flooded the log with metric-write failures while the
# Monitoring API was disabled; that API is enabled now, so the noise is
# gone.

set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "run as root: sudo bash $0" >&2
  exit 1
fi

CONTAINER="${CONTAINER:-sotf-server}"

echo "== installing the Ops Agent =="
curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
bash add-google-cloud-ops-agent-repo.sh --also-install
rm -f add-google-cloud-ops-agent-repo.sh

echo
echo "== pointing it at the container log =="
# Docker writes each container's stdout to a JSON file under this path.
# Tailing it is enough; nothing has to change about how the game runs.
install -d /etc/google-cloud-ops-agent
cat > /etc/google-cloud-ops-agent/config.yaml <<'YAML'
logging:
  receivers:
    sotf_container:
      type: files
      include_paths:
        - /var/lib/docker/containers/*/*-json.log
  processors:
    # Docker wraps each line as {"log":"…","stream":"stdout","time":"…"};
    # unwrapping it puts the game's own text where the billing filter
    # looks for it.
    unwrap_docker:
      type: parse_json
      field: message
    lift_log_line:
      type: modify_fields
      fields:
        jsonPayload.message:
          move_from: jsonPayload.log
  service:
    pipelines:
      sotf:
        receivers: [sotf_container]
        processors: [unwrap_docker, lift_log_line]
YAML

systemctl restart google-cloud-ops-agent

echo
echo "== status =="
systemctl is-active google-cloud-ops-agent
sleep 5
journalctl -u google-cloud-ops-agent -n 15 --no-pager || true

echo
echo "Fertig. Pruefen, ob Zeilen ankommen (dauert bis zu einer Minute):"
echo
cat <<'CHECK'
gcloud logging read \
  'resource.type="gce_instance" AND "Steam auth successful"' \
  --limit=5 --freshness=1h \
  --project=gen-lang-client-0146272192 \
  --format='value(timestamp, jsonPayload.message)'
CHECK
