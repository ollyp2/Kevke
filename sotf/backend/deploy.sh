#!/usr/bin/env bash
# Deploy the sotf-control Cloud Function. Run locally (needs gcloud creds).
#
# First run also seeds the Firestore server registry. Re-runs are safe.

set -euo pipefail

PROJECT="${PROJECT:-gen-lang-client-0146272192}"
REGION="${REGION:-europe-west3}"
FUNCTION="${FUNCTION:-sotf-control}"
ZONE="${ZONE:-europe-west3-a}"
INSTANCE="${INSTANCE:-sotf-server}"

if [[ -z "${API_TOKEN:-}" ]]; then
  echo "API_TOKEN not set. Generate one and keep it out of git:" >&2
  echo "  export API_TOKEN=\$(openssl rand -hex 32)" >&2
  exit 1
fi

echo "== enabling APIs =="
gcloud services enable \
  cloudfunctions.googleapis.com run.googleapis.com \
  cloudbuild.googleapis.com firestore.googleapis.com \
  --project="$PROJECT"

echo
echo "== deploying $FUNCTION =="
gcloud functions deploy "$FUNCTION" \
  --project="$PROJECT" \
  --region="$REGION" \
  --gen2 \
  --runtime=python312 \
  --source="$(dirname "$0")" \
  --entry-point=sotf_control \
  --trigger-http \
  --allow-unauthenticated \
  --memory=512Mi \
  --timeout=60s \
  --set-env-vars="PROJECT_ID=${PROJECT},API_TOKEN=${API_TOKEN},HOURLY_RATE_EUR=0.17"

URL="$(gcloud functions describe "$FUNCTION" --project="$PROJECT" \
        --region="$REGION" --gen2 --format='value(serviceConfig.uri)')"

echo
echo "== deployed =="
echo "Base URL: $URL"
echo
echo "Test:"
echo "  curl -H \"Authorization: Bearer \$API_TOKEN\" $URL/servers"
echo
echo "Seed the server registry once (Firestore must be in Native mode):"
cat <<SEED

gcloud firestore databases create --location="$REGION" --project="$PROJECT" 2>/dev/null || true

python3 - <<'PY'
from google.cloud import firestore
db = firestore.Client(project="$PROJECT")
db.collection("servers").document("sotf-main").set({
    "name": "Kevkes SotF",
    "game": "sons-of-the-forest",
    "zone": "$ZONE",
    "instance": "$INSTANCE",
    "logo": "sotf",
    "hourlyRateEur": 0.17,
    "live": {},
}, merge=True)
print("seeded sotf-main")
PY
SEED
