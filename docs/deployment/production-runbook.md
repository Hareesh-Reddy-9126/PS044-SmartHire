# SmartHire production runbook

Single-VM deployment. One public HTTPS hostname serves the React SPA and proxies the API, so the
browser only ever talks to one origin. PostgreSQL, Redis, Eureka and the Spring Boot services stay
on the private Docker network and are never published.

```
Internet
   |  https://<DOMAIN>            (80/443 only)
   v
frontend container (Caddy)  ── static SPA from /srv, SPA fallback to index.html
   |  /api/*, /oauth2/*, /actuator/*
   v
api-gateway:8080  ── Eureka (lb://) ──> auth, job, application, recruitment,
   |                                     document, notification, ai services
   +──> postgres:5432   (internal only)
   +──> redis:6379      (internal only)
```

## 1. Sizing

| Item | Value |
| --- | --- |
| Measured idle footprint | 11 containers, ~4.8 GiB RSS |
| Compose memory limits | ~6.5 GiB total |
| Target VM | Oracle Cloud Always Free Ampere A1: 2 OCPU / 12 GB RAM / 200 GB block storage / 10 TB egress |
| Boot volume | 100 GB is enough (images + database + backups) |

The free Ampere allowance was reduced to 2 OCPU / 12 GB in July 2026. The stack fits with headroom
for the OS, Caddy and a cold image build; a 2 GiB swap file (created by the bootstrap script) is an
OOM cushion, not a substitute for RAM.

## 2. Prerequisites

- An Oracle Cloud account (card and phone verification are required by Oracle).
- An Ubuntu 22.04 or 24.04 VM with the Ampere A1 shape.
- VCN security list ingress: TCP 22, 80, 443 from `0.0.0.0/0`.
- The deployment SSH key. The public half is pasted into the instance creation form; the private
  half never leaves the operator machine.
- A hostname:
  - `<public-ip>.sslip.io` — no account, resolves to the IP encoded in the name. Recommended.
  - A DuckDNS subdomain if a stable name is preferred (requires a DuckDNS account and token).

## 3. Provision the VM

In the Oracle console: **Compute → Instances → Create instance**.

1. Image: Canonical Ubuntu 24.04.
2. Shape: Ampere → `VM.Standard.A1.Flex`, 2 OCPU, 12 GB memory.
3. Boot volume: 100 GB.
4. Paste the **public** SSH key.
5. Create, then add the VCN ingress rules for 80/443.

On the VM:

```bash
sudo install -d -o "$USER" -g "$USER" /opt/smarthire
git clone https://github.com/Hareesh-Reddy-9126/PS044-SmartHire.git /opt/smarthire
cd /opt/smarthire
sudo ./deploy/scripts/bootstrap-vm.sh
```

The script installs Docker Engine + Compose v2, adds swap, opens 22/80/443 in the host firewall and
creates `/opt/smarthire`, `/var/backups/smarthire` and `/etc/smarthire`. It is idempotent. Log out
and back in (or `newgrp docker`) before running `docker` without `sudo`.

## 4. Generate the environment file

```bash
cd /opt/smarthire
DOMAIN=smarthire.<public-ip>.sslip.io \
ACME_EMAIL=you@example.com \
NOTIFICATION_SENDER_TYPE=logging \
./deploy/scripts/init-env.sh
```

`init-env.sh` writes `deploy/.env` with a random database password and a fresh 2048-bit PKCS#8 RSA
JWT key, mode 600, and never prints a secret. Use `NOTIFICATION_SENDER_TYPE=smtp` plus
`SMTP_HOST`/`SMTP_FROM` (and credentials) when a mail provider is available; `logging` records
notifications without sending email.

Then create the backup environment file used by the systemd timer:

```bash
sudo install -d -m 755 /etc/smarthire
sudo tee /etc/smarthire/backup.env >/dev/null <<'EOF'
BACKUP_DIR=/var/backups/smarthire
BACKUP_RETENTION_DAYS=7
EOF
```

## 5. Deploy

```bash
cd /opt/smarthire
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml \
  --env-file deploy/.env up -d --build
```

The eight Java service images share one Maven build stage, so the reactor is compiled once and
reused by BuildKit; only the AI service and the frontend have separate build stages.

Wait for every container to report healthy:

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml \
  --env-file deploy/.env ps
```

## 6. Verify

```bash
DOMAIN=smarthire.<public-ip>.sslip.io
curl -sSI "https://$DOMAIN/" | head -20          # 200, text/html, HSTS
curl -sSI "https://$DOMAIN/jobs" | head -5       # 200, SPA fallback (not 404)
curl -sS  "https://$DOMAIN/actuator/health"      # {"status":"UP"}
curl -sS  "https://$DOMAIN/api/v1/jobs" | head -c 400
```

TLS is issued by Let's Encrypt on first request; if the first `curl` fails, watch
`docker compose ... logs -f frontend` and confirm DNS resolves to the VM and port 80 is reachable.

## 7. First administrator

Admin self-registration is disabled unless `AUTH_ADMIN_SIGNUP_CODE` is set. To create the first
administrator:

1. Set a temporary, strong code in `deploy/.env` and restart the auth service:

   ```bash
   docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml \
     --env-file deploy/.env up -d auth-service
   ```

2. Register with `role: "ADMIN"` and the code in `adminInviteCode` (password: 12+ characters):

   ```bash
   curl -sS -X POST "https://$DOMAIN/api/v1/auth/register" \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@example.com","password":"<strong-password>","role":"ADMIN","adminInviteCode":"<temporary-code>"}'
   ```

3. Blank `AUTH_ADMIN_SIGNUP_CODE` in `deploy/.env` and restart `auth-service` again. Open admin
   signup must not stay enabled.

## 8. Backups

```bash
sudo cp deploy/systemd/smarthire-backup.service /etc/systemd/system/
sudo cp deploy/systemd/smarthire-backup.timer   /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now smarthire-backup.timer
sudo systemctl list-timers smarthire-backup.timer
```

Run one immediately and verify it restores into a disposable container:

```bash
sudo systemctl start smarthire-backup.service
sudo BACKUP_DIR=/var/backups/smarthire ./deploy/scripts/restore-verify.sh
```

See [backup-restore.md](backup-restore.md) for the recovery procedure and off-VM copies.

## 9. Redeploy and rollback

Redeploy the current commit:

```bash
cd /opt/smarthire
git fetch origin && git status --short          # must be clean before deploying
git checkout <commit>
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml \
  --env-file deploy/.env up -d --build
```

Rollback: check out the previous known-good commit and repeat the command. Images are rebuilt from
source, so no registry is required. Database migrations are Flyway forward-only; take a backup
before deploying a commit that adds a migration, and restore it if a rollback crosses a migration.

## 10. Secret rotation

| Secret | Procedure |
| --- | --- |
| `POSTGRES_PASSWORD` | `ALTER USER` in Postgres, update `deploy/.env`, recreate the stack. |
| `AUTH_JWT_PRIVATE_KEY` | Generate a new PKCS#8 RSA key, replace the value, restart `auth-service`. All issued access/refresh tokens become invalid; users sign in again. |
| `AUTH_ADMIN_SIGNUP_CODE` | Set only while creating an administrator, then blank and restart `auth-service`. |
| SMTP credentials | Update `deploy/.env`, restart `notification-service`. |

`deploy/.env` is git-ignored and must never be committed, copied into an image or pasted into a
chat.

## 11. Troubleshooting

| Symptom | Check |
| --- | --- |
| TLS certificate not issued | DNS points at the VM; VCN and host firewall allow 80/443; `frontend` logs. |
| `502` from Caddy | `docker compose ps` — gateway healthy? Caddy proxies to `api-gateway:8080` on the compose network. |
| Service restarts with OOM | `docker stats`; the compose limits assume the 12 GB shape. Reduce `JAVA_TOOL_OPTIONS` only after measuring. |
| `503` from the gateway | Eureka registration: `docker compose logs service-registry`; services register within ~60 s of start. |
| Notification service exits at boot | `NOTIFICATION_SENDER_TYPE=smtp` requires `NOTIFICATION_SMTP_HOST` and `NOTIFICATION_SMTP_FROM`. Use `logging` until a provider is configured. |
| Admin registration rejected | `AUTH_ADMIN_SIGNUP_CODE` is blank or the code does not match. |

## 12. Cost guardrails

The deployment uses only Always Free resources: one Ampere A1 instance (2 OCPU / 12 GB), its 100 GB
boot volume, and 10 TB/month egress. No managed database, load balancer, NAT gateway, reserved IP or
paid DNS is required. Oracle may reclaim idle Always Free capacity; keep the instance active and
monitor the account's billing page for unexpected charges. Do not upgrade the account or enable
paid services without explicit approval.
