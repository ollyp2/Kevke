#!/usr/bin/env bash
# Promote the VM's current EPHEMERAL external IP to a STATIC reservation
# without changing the address. Run locally, not on the VM.
#
# Why: ephemeral IPs are re-assigned on every VM start, so the trigger link
# (and anyone with the address bookmarked) would break after each idle
# shutdown/restart.

set -euo pipefail

PROJECT="${PROJECT:-gen-lang-client-0146272192}"
ZONE="${ZONE:-europe-west3-a}"
REGION="${REGION:-europe-west3}"
INSTANCE="${INSTANCE:-sotf-server}"
ADDR_NAME="${ADDR_NAME:-sotf-server-ip}"

gcloud() { command gcloud --project="$PROJECT" "$@"; }

echo "== current external IP on $INSTANCE =="
CURRENT_IP="$(gcloud compute instances describe "$INSTANCE" --zone="$ZONE" \
  --format='value(networkInterfaces[0].accessConfigs[0].natIP)')"
ACCESS_CFG="$(gcloud compute instances describe "$INSTANCE" --zone="$ZONE" \
  --format='value(networkInterfaces[0].accessConfigs[0].name)')"
echo "IP:            $CURRENT_IP"
echo "access-config: $ACCESS_CFG"

echo
echo "== existing static reservations in $REGION =="
gcloud compute addresses list --regions="$REGION" \
  --format='table(name,address,status,users)'

# --- Promote the current ephemeral IP to static ------------------------------
# This reserves the SAME address, so nothing external needs to change.
# Uncomment to apply:
#
# gcloud compute addresses create "$ADDR_NAME" \
#   --region="$REGION" \
#   --addresses="$CURRENT_IP"
#
# `gcloud compute addresses create --addresses=<IP>` picks up an ephemeral
# IP already in use and converts it in place — no downtime, no address
# change. Verify:
#
# gcloud compute addresses describe "$ADDR_NAME" --region="$REGION" \
#   --format='value(status,address,users)'
#
# Expected: status=IN_USE, users contains the sotf-server instance.
