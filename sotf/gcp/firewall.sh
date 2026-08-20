#!/usr/bin/env bash
# GCP firewall setup for the SOTF dedicated server.
# Run from your local machine (has gcloud creds), NOT from the VM.
#
# Ports the SOTF dedicated server actually needs (all UDP):
#   8766   game
#   27016  Steam query
#   9700   BlobSync (world sync — silent break if missing)
#
# There are NO TCP ports for the game itself. TCP 22 for SSH is handled by
# GCE's default-allow-ssh rule (or IAP), not by this game-ports rule.

set -euo pipefail

PROJECT="${PROJECT:-gen-lang-client-0146272192}"
NETWORK="${NETWORK:-default}"
RULE="${RULE:-sotf-game-ports}"
TAG="${TAG:-sotf-server}"

gcloud() { command gcloud --project="$PROJECT" "$@"; }

echo "== current rule =="
gcloud compute firewall-rules describe "$RULE" \
  --format='yaml(name,network,direction,sourceRanges,targetTags,allowed)' || true

echo
echo "== VM network tags (rule only applies if the VM carries \$TAG) =="
gcloud compute instances describe sotf-server --zone=europe-west3-a \
  --format='value(tags.items)'

# --- Fix: replace the rule with the correct UDP-only allowlist ---------------
# `firewall-rules update` REPLACES --allow wholesale, which is what we want
# here: drop tcp:9700 and any other stragglers, keep exactly the three UDP
# ports the game needs.
#
# Uncomment to apply:
#
# gcloud compute firewall-rules update "$RULE" \
#   --allow=udp:8766,udp:27016,udp:9700
#
# If the rule does not exist yet on a fresh project, create it instead:
#
# gcloud compute firewall-rules create "$RULE" \
#   --network="$NETWORK" \
#   --direction=INGRESS \
#   --action=ALLOW \
#   --rules=udp:8766,udp:27016,udp:9700 \
#   --source-ranges=0.0.0.0/0 \
#   --target-tags="$TAG"
#
# And make sure the VM carries the target tag:
#
# gcloud compute instances add-tags sotf-server \
#   --zone=europe-west3-a --tags="$TAG"

echo
echo "== verify after applying =="
echo "gcloud compute firewall-rules describe $RULE --format='value(allowed)'"
