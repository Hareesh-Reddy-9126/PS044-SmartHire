#!/usr/bin/env bash
# Runs on the target VM. Extracts an uploaded source bundle and brings the production stack up.
#
# Invoked by deploy/scripts/deploy-remote.ps1; can also be run by hand:
#   sudo ./deploy/scripts/remote-deploy.sh <domain> <acme-email> [logging|smtp]
#
# Idempotent: re-running updates the checkout, keeps the existing deploy/.env (so secrets and the
# database stay intact), rebuilds changed images and reconciles the running stack.
set -euo pipefail

DOMAIN="${1:?usage: remote-deploy.sh <domain> <acme-email> [logging|smtp]}"
ACME_EMAIL="${2:?usage: remote-deploy.sh <domain> <acme-email> [logging|smtp]}"
SENDER_TYPE="${3:-logging}"

BUNDLE="${BUNDLE:-/tmp/smarthire-src.tar.gz}"
TARGET="${TARGET:-/opt/smarthire}"
COMPOSE=(docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml --env-file deploy/.env)

log() { printf '\n[remote-deploy] %s\n' "$*"; }
die() { printf '[remote-deploy] ERROR: %s\n' "$*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo."
[[ -f "$BUNDLE" ]] || die "Source bundle $BUNDLE not found."

# ---- Host bootstrap (only when Docker is missing) -------------------------------------------
if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  log "Docker already installed: $(docker --version)"
else
  log "Docker missing; running bootstrap"
  install -d "$TARGET"
  tar -xzf "$BUNDLE" -C "$TARGET"
  bash "$TARGET/deploy/scripts/bootstrap-vm.sh"
fi

# ---- Update the checkout, preserving generated secrets --------------------------------------
install -d "$TARGET"
if [[ -f "$TARGET/deploy/.env" ]]; then
  log "Keeping existing $TARGET/deploy/.env"
  cp -p "$TARGET/deploy/.env" /tmp/smarthire-env.keep
fi

log "Extracting source bundle into $TARGET"
tar -xzf "$BUNDLE" -C "$TARGET" --overwrite

if [[ -f /tmp/smarthire-env.keep ]]; then
  mv -f /tmp/smarthire-env.keep "$TARGET/deploy/.env"
  chmod 600 "$TARGET/deploy/.env"
fi

cd "$TARGET"

# ---- Generate secrets on first deploy --------------------------------------------------------
if [[ ! -f deploy/.env ]]; then
  # Optional SMTP credentials (SMTP_USERNAME/SMTP_PASSWORD/SMTP_PORT) arrive as a separate file so
  # they never appear on a command line or in shell history.
  if [[ -f /tmp/smarthire-smtp.env ]]; then
    chmod 600 /tmp/smarthire-smtp.env
    set -a
    # shellcheck disable=SC1091
    . /tmp/smarthire-smtp.env
    set +a
  fi
  log "No deploy/.env yet; generating one (sender=$SENDER_TYPE)"
  DOMAIN="$DOMAIN" ACME_EMAIL="$ACME_EMAIL" NOTIFICATION_SENDER_TYPE="$SENDER_TYPE" \
    bash deploy/scripts/init-env.sh
  rm -f /tmp/smarthire-smtp.env
fi

# ---- Build and start -------------------------------------------------------------------------
log "Building images (first build takes ~10-20 min on 2 OCPU; Maven cache is reused afterwards)"
"${COMPOSE[@]}" build

log "Starting the stack"
"${COMPOSE[@]}" up -d --remove-orphans

# ---- Health gate -----------------------------------------------------------------------------
log "Waiting for every container with a healthcheck to report healthy (max 10 min)"
deadline=$((SECONDS + 600))
while :; do
  pending=0
  unhealthy=""
  for cid in $(docker ps -q --filter "label=com.docker.compose.project=smarthire"); do
    name="$(docker inspect --format '{{.Name}}' "$cid" | sed 's#^/##')"
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$cid")"
    case "$status" in
      healthy|none) ;;
      unhealthy) unhealthy="$unhealthy $name"; pending=1 ;;
      *) pending=1 ;;
    esac
  done
  [[ $pending -eq 0 ]] && break
  if [[ $SECONDS -ge $deadline ]]; then
    log "Timed out waiting for health. Current status:"
    "${COMPOSE[@]}" ps
    [[ -n "$unhealthy" ]] && die "Unhealthy containers:$unhealthy"
    die "Some containers never became healthy."
  fi
  sleep 5
done

log "All containers healthy"
"${COMPOSE[@]}" ps

log "Done. Public URL: https://$DOMAIN"
