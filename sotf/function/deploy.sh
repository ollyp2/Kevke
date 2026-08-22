#!/usr/bin/env bash
# Deploy sotf-control — start/stop/status behind one token.
#
#   export TOKEN=$(openssl rand -hex 24)
#   bash deploy.sh
#
# Keeps the existing sotf-start-trigger untouched; delete that one once
# the app points at this function.

set -euo pipefail

PROJECT="${PROJECT:-gen-lang-client-0146272192}"
REGION="${REGION:-europe-west3}"
ZONE="${ZONE:-europe-west3-a}"
INSTANCE="${INSTANCE:-sotf-server}"
NAME="${NAME:-sotf-control}"

if [[ -z "${TOKEN:-}" ]]; then
  echo "TOKEN not set. Generate one (and keep it out of git):" >&2
  echo "  export TOKEN=\$(openssl rand -hex 24)" >&2
  exit 1
fi

# --docker-repository is spelled out because gcloud fails to look it up
# on redeploys of an existing gen2 function:
#   AttributeError: 'NoneType' object has no attribute 'dockerRepository'
# The path below is the default repo gcloud creates on the first deploy.
gcloud functions deploy "$NAME" \
  --project="$PROJECT" \
  --region="$REGION" \
  --gen2 \
  --runtime=python312 \
  --source="$(dirname "$0")" \
  --entry-point=sotf_control \
  --trigger-http \
  --allow-unauthenticated \
  --memory=256Mi \
  --timeout=30s \
  --docker-repository="projects/${PROJECT}/locations/${REGION}/repositories/gcf-artifacts" \
  --set-env-vars="TOKEN=${TOKEN},PROJECT_ID=${PROJECT},ZONE=${ZONE},INSTANCE=${INSTANCE},QUERY_PORT=27016"

URL="$(gcloud functions describe "$NAME" --project="$PROJECT" \
        --region="$REGION" --gen2 --format='value(serviceConfig.uri)')"

echo
echo "Base URL: $URL"
echo
echo "Deine Links:"
echo "  Start:  $URL?token=$TOKEN&action=start"
echo "  Stop:   $URL?token=$TOKEN&action=stop"
echo "  Status: $URL?token=$TOKEN&action=status"
echo
echo "Der Service-Account der Function braucht compute.instanceAdmin.v1:"
echo
PROJNUM="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')"
cat <<PERM
gcloud projects add-iam-policy-binding $PROJECT \\
  --member="serviceAccount:${PROJNUM}-compute@developer.gserviceaccount.com" \\
  --role="roles/compute.instanceAdmin.v1"
PERM
