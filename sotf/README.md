# SOTF Dedicated Server on GCE

Everything needed to reproduce the Sons of the Forest dedicated server VM
from scratch, plus operational answers for the running one.

```
sotf/
├── docker-compose.yml            # compose v2, correct UDP mappings, env-driven
├── .env.example                  # SERVERNAME / SERVERPASSWORD placeholders
├── setup-sotf.sh                 # idempotent VM bootstrap (run as root)
├── idle-shutdown/
│   ├── sotf-idle-shutdown.sh     # /usr/local/bin target
│   └── sotf-idle-shutdown.service
└── gcp/
    ├── firewall.sh               # correct UDP-only rule
    └── static-ip.sh              # promote ephemeral IP to static
```

## Reproduce on a fresh VM

Everything below assumes the target user is `kradtke79`; override with
`SOTF_USER=... SOTF_DIR=... bash setup-sotf.sh` if not.

1. **Create the VM** (locally, with `gcloud`):

   ```bash
   gcloud compute instances create sotf-server \
     --project=gen-lang-client-0146272192 \
     --zone=europe-west3-a \
     --machine-type=e2-standard-4 \
     --image-family=debian-12 --image-project=debian-cloud \
     --boot-disk-size=40GB \
     --tags=sotf-server
   ```

2. **Firewall + static IP** (locally): run `gcp/firewall.sh` and
   `gcp/static-ip.sh`. Both are dry-run by default; read the commented
   `gcloud …` lines, un-comment, re-run.

3. **Bootstrap the VM**:

   ```bash
   gcloud compute scp --recurse --zone=europe-west3-a ./sotf sotf-server:~
   gcloud compute ssh sotf-server --zone=europe-west3-a \
     --command='sudo bash ~/sotf/setup-sotf.sh'
   ```

4. **Set credentials**: `sudo nano /home/kradtke79/sotf/.env`, then
   `cd /home/kradtke79/sotf && docker compose up -d`.

5. **Watch it come up**: `docker logs -f sotf-server`. When the server is
   ready and idle you'll see `Set target framerate to 30` repeating — that
   is the marker the idle-shutdown watches for.

## Operations

### 1. Test the idle-shutdown without waiting 15 minutes

The systemd unit ships with `SOTF_IDLE_TIMEOUT_SECONDS=900`. Two ways
to verify without actually powering the VM off:

**A. One-off dry-run with a 60-second timeout (recommended)**

```bash
sudo SOTF_IDLE_TIMEOUT_SECONDS=60 SOTF_CHECK_INTERVAL_SECONDS=10 \
  /usr/local/bin/sotf-idle-shutdown.sh --dry-run
```

You should see one `idle for Ns / 60s` line every 10s, and after ~60s:

```
[sotf-idle-shutdown] DRY RUN: would run /sbin/shutdown -h now
```

Join the server from your client during the test — the counter should
reset and log `activity detected, resetting idle counter`.

**B. Override the real service temporarily**

```bash
sudo systemctl edit sotf-idle-shutdown
# in the drop-in:
[Service]
Environment=SOTF_IDLE_TIMEOUT_SECONDS=60

sudo systemctl restart sotf-idle-shutdown
journalctl -u sotf-idle-shutdown -f
```

`systemctl edit --revert sotf-idle-shutdown` puts it back to 900 s. This
one *will* poweroff — only use it if you're OK with the VM shutting down
during the test.

### 2. Firewall cleanup

The `sotf-game-ports` rule currently contains `tcp:9700`. SOTF uses no
TCP for the game — BlobSync is UDP. The correct allowlist is:

| Port  | Proto | Purpose        |
|-------|-------|----------------|
| 8766  | UDP   | game           |
| 27016 | UDP   | Steam query    |
| 9700  | UDP   | BlobSync       |

Nothing else. SSH (TCP 22) lives on `default-allow-ssh` or IAP — leave
it alone. Apply the fix with the commented block in `gcp/firewall.sh`
(uses `firewall-rules update`, which replaces `--allow` wholesale —
exactly what we want).

Verify:

```bash
gcloud compute firewall-rules describe sotf-game-ports \
  --format='value(allowed)'
# expected: [{'IPProtocol': 'udp', 'ports': ['8766','27016','9700']}]
```

### 3. Static external IP

Check whether the address is currently static:

```bash
gcloud compute addresses list --regions=europe-west3
```

If the current natIP of `sotf-server` does not appear there, it is
ephemeral and will change on next VM restart. Reserve **the same
address** (no downtime, no address change) with the commented block in
`gcp/static-ip.sh`:

```bash
gcloud compute addresses create sotf-server-ip \
  --region=europe-west3 \
  --addresses=<CURRENT_NAT_IP>
```

That promotes the existing ephemeral IP in place. Your trigger link keeps
working.

### 4. Change SERVERNAME / SERVERPASSWORD (savegame safe)

The savegame is on the host at `/home/kradtke79/sotf/userdata`, mounted
into the container. Recreating the container does **not** touch it, and
the compose v1 → v2 note below doesn't either.

Recommended flow — put the values in `.env`, not into `docker-compose.yml`
directly:

```bash
cd /home/kradtke79/sotf

# 0. Belt-and-braces savegame backup:
sudo cp -a userdata userdata.bak.$(date +%F)

# 1. Edit .env (if you don't have one yet, copy .env.example first):
sudoedit .env
#   SOTF_SERVERNAME=Kevkes-Server
#   SOTF_SERVERPASSWORD=<your new password>

# 2. Recreate. On docker-compose v1 you MUST down first, otherwise
#    you hit the KeyError: 'ContainerConfig' bug on recreate:
sudo docker-compose down
sudo docker-compose up -d
#    (Once you're on compose v2, `docker compose up -d` is enough.)

# 3. Confirm the container came back and the world is intact:
docker logs --tail 50 sotf-server
ls -la userdata          # untouched, same mtimes
```

Why the savegame survives: `userdata` is a bind mount, so it lives on
the host filesystem regardless of container lifecycle. The name/password
are read from environment at process start; they do not rewrite anything
inside `userdata`.

If you'd rather edit `docker-compose.yml` directly (the current file on
the VM still has the `SERVERNAME=DeinServerName` placeholders): same
sequence — edit, `down`, `up -d`. Same guarantees. `.env` is just less
error-prone next time.

### 5. Fix the Ops Agent log spam

Cleanest: uninstall the agent. You're not using Cloud Monitoring for
this VM, and enabling `monitoring.googleapis.com` just to silence a
service you don't consume is more moving parts, not fewer.

```bash
sudo apt-get purge -y google-cloud-ops-agent
sudo rm -rf /etc/google-cloud-ops-agent /var/log/google-cloud-ops-agent
```

`setup-sotf.sh` already does this on fresh VMs (`remove_ops_agent_if_present`).

Alternative if you *do* want VM metrics later:

```bash
gcloud services enable monitoring.googleapis.com logging.googleapis.com \
  --project=gen-lang-client-0146272192
```

…and make sure the VM's service account has `roles/monitoring.metricWriter`
and `roles/logging.logWriter`. Overkill for a hobby game server.

## Notes

- **docker-compose v1 vs v2**: the setup script installs v2
  (`docker-compose-plugin`). Once you're on v2, drop the workaround —
  `docker compose up -d` recreates containers without the
  `ContainerConfig` bug. v1 stays on the VM until you `apt purge
  docker-compose`; both can coexist.
- **Trigger link**: the "start VM" trigger you use lives outside this
  repo. Whatever fronts it, it just needs to be able to call
  `compute.instances.start` on `sotf-server`. If it's a Cloud Function
  with the default runtime SA, that SA needs `roles/compute.instanceAdmin.v1`
  scoped to the VM (or, safer, a custom role with just `compute.instances.start`).
- **Log-marker fragility**: idle detection keys on the string
  `Set target framerate`. If the upstream image changes its idle log
  line, edit `IDLE_MARKER` in `idle-shutdown/sotf-idle-shutdown.sh`.
