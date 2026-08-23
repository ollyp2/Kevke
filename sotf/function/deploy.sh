#!/usr/bin/env bash
# Deploy sotf-control — power, backups and billing behind one token.
#
#   export TOKEN=$(openssl rand -hex 24)
#   bash deploy.sh

set -euo pipefail

PROJECT="${PROJECT:-gen-lang-client-0146272192}"
REGION="${REGION:-europe-west3}"
ZONE="${ZONE:-europe-west3-a}"
INSTANCE="${INSTANCE:-sotf-server}"
NAME="${NAME:-sotf-control}"
HOURLY_RATE="${HOURLY_RATE:-0.17}"

# The token is the only thing between the open internet and a function
# that can stop the VM and delete backups, so it is checked rather than
# trusted. A placeholder copied out of a chat message once made it all
# the way into a live deploy; that must fail here, not in production.
if [[ -z "${TOKEN:-}" ]]; then
  echo "TOKEN not set. Generate one (and keep it out of git):" >&2
  echo "  export TOKEN=\$(openssl rand -hex 24)" >&2
  exit 1
fi

if [[ ${#TOKEN} -lt 20 ]]; then
  echo "TOKEN is only ${#TOKEN} characters. Use at least 20:" >&2
  echo "  export TOKEN=\$(openssl rand -hex 24)" >&2
  exit 1
fi

case "${TOKEN,,}" in
  *dein_token*|*your_token*|*changeme*|*platzhalter*|*placeholder*|*token*|*secret*|*password*|*passwort*)
    echo "TOKEN looks like a placeholder, not a secret. Generate a real one:" >&2
    echo "  export TOKEN=\$(openssl rand -hex 24)" >&2
    exit 1
    ;;
esac

PROJNUM="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
SA="${PROJNUM}-compute@developer.gserviceaccount.com"

echo "== enabling APIs =="
# Monitoring supplies the measured uptime the billing action reports.
gcloud services enable monitoring.googleapis.com --project="$PROJECT"

echo
echo "== granting roles to $SA =="
# compute.instanceAdmin.v1 covers start/stop plus snapshot and disk work,
# monitoring.viewer reads the measured uptime, logging.viewer reads the
# container log the per-player split is built from, and logWriter lets
# the VM's own agent ship that log in the first place.
for ROLE in roles/compute.instanceAdmin.v1 roles/monitoring.viewer roles/logging.viewer roles/logging.logWriter; do
  gcloud projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:${SA}" \
    --role="$ROLE" \
    --condition=None \
    --quiet --format='value(etag)' >/dev/null
  echo "  $ROLE"
done

echo
echo "== deploying $NAME =="
# --docker-repository is spelled out because gcloud fails to look it up
# on redeploys of an existing gen2 function:
#   AttributeError: 'NoneType' object has no attribute 'dockerRepository'
# A restore swaps a boot disk and waits on several zone operations, so the
# timeout has to be well above the 30s that power actions need.
gcloud functions deploy "$NAME" \
  --project="$PROJECT" \
  --region="$REGION" \
  --gen2 \
  --runtime=python312 \
  --source="$(dirname "$0")" \
  --entry-point=sotf_control \
  --trigger-http \
  --allow-unauthenticated \
  --memory=512Mi \
  --timeout=540s \
  --docker-repository="projects/${PROJECT}/locations/${REGION}/repositories/gcf-artifacts" \
  --set-env-vars="TOKEN=${TOKEN},PROJECT_ID=${PROJECT},ZONE=${ZONE},INSTANCE=${INSTANCE},QUERY_PORT=27016,HOURLY_RATE=${HOURLY_RATE}"

URL="$(gcloud functions describe "$NAME" --project="$PROJECT" \
        --region="$REGION" --gen2 --format='value(serviceConfig.uri)')"

echo
echo "Base URL: $URL"
echo
echo "Deine Links:"
for A in start stop status backups billing; do
  printf '  %-8s %s?token=%s&action=%s\n' "$A:" "$URL" "$TOKEN" "$A"
done
echo
echo "Backup anlegen:  $URL?token=$TOKEN&action=backup&label=vor-dem-bunker"
# No angle-bracket placeholder here: a shell reads "<" as a redirection,
# so a line like that pasted whole does something other than it reads.
echo "Zuruecksetzen:   $URL?token=$TOKEN&action=restore&name=NAME-AUS-DER-BACKUP-LISTE"
