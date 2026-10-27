#!/usr/bin/env bash
# Creates a SmartHire administrator and closes the invitation path again.
#
# Usage (on the VM, from the checkout at /opt/smarthire):
#   sudo ./deploy/scripts/create-admin.sh admin@example.com
#
# If AUTH_ADMIN_SIGNUP_CODE is empty, the script issues a random one-time code, recreates
# auth-service so the code takes effect, registers the account, then blanks the code and recreates
# the service again. The code therefore never has to be handled by the operator and is never left
# enabled.
#
# A strong password is generated locally and stored, with the e-mail address, in
# /root/smarthire-admin-initial.txt (mode 600). The password is never printed to the terminal, to a
# log, or to this script's stdout.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/deploy/.env}"
OUT_FILE="${OUT_FILE:-/root/smarthire-admin-initial.txt}"
COMPOSE=(docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.prod.yml --env-file deploy/.env)

log() { printf '\n[create-admin] %s\n' "$*"; }
die() { printf '[create-admin] ERROR: %s\n' "$*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo so the credential file can be written to /root."
[[ -f "$ENV_FILE" ]] || die "$ENV_FILE not found. Run deploy/scripts/init-env.sh first."

read_env() {
  # Reads a single KEY=value line without sourcing the file (values may contain shell metacharacters).
  sed -n "s/^$1=//p" "$ENV_FILE" | head -n 1
}

write_env() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

wait_for_auth() {
  local status=""
  for _ in $(seq 1 90); do
    status="$(docker inspect --format '{{.State.Health.Status}}' smarthire-auth-service-1 2>/dev/null || true)"
    [[ "$status" == "healthy" ]] && return 0
    sleep 2
  done
  return 1
}

DOMAIN="$(read_env DOMAIN)"
ADMIN_EMAIL="${1:-${ADMIN_EMAIL:-}}"

[[ -n "$DOMAIN" ]] || die "DOMAIN is empty in $ENV_FILE."
[[ -n "$ADMIN_EMAIL" ]] || die "Usage: sudo $0 admin@example.com"
[[ "$ADMIN_EMAIL" =~ ^[^\"\\[:space:]]+@[^\"\\[:space:]]+$ ]] \
  || die "ADMIN_EMAIL must be a plain e-mail address (no quotes, spaces or backslashes)."

# 24 URL-safe characters: comfortably above the 12-character policy and easy to paste.
ADMIN_PASSWORD="${ADMIN_PASSWORD:-$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | cut -c1-24)}"
[[ ${#ADMIN_PASSWORD} -ge 12 ]] || die "ADMIN_PASSWORD must be at least 12 characters."
[[ "$ADMIN_PASSWORD" != *'"'* && "$ADMIN_PASSWORD" != *'\'* ]] \
  || die "ADMIN_PASSWORD must not contain double quotes or backslashes."

cd "$REPO_ROOT"

CODE="$(read_env AUTH_ADMIN_SIGNUP_CODE)"
GENERATED=0
if [[ -z "$CODE" ]]; then
  log "No invitation code configured; issuing a one-time code"
  CODE="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | cut -c1-40)"
  GENERATED=1
  write_env AUTH_ADMIN_SIGNUP_CODE "$CODE"
  chmod 600 "$ENV_FILE"
  "${COMPOSE[@]}" up -d --no-deps auth-service
  wait_for_auth || die "auth-service did not become healthy after enabling the invitation code."
fi

log "Registering administrator $ADMIN_EMAIL against https://$DOMAIN"
PAYLOAD="$(printf '{"email":"%s","password":"%s","role":"ADMIN","adminInviteCode":"%s"}' \
  "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$CODE")"

HTTP_CODE="$(
  printf '%s' "$PAYLOAD" \
    | curl -sS --max-time 30 -o /tmp/smarthire-admin-register.out -w '%{http_code}' \
        -H 'Content-Type: application/json' --data-binary @- \
        "https://$DOMAIN/api/v1/auth/register"
)"
unset PAYLOAD

if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" ]]; then
  printf '[create-admin] Response body: %s\n' "$(head -c 400 /tmp/smarthire-admin-register.out 2>/dev/null || true)" >&2
  rm -f /tmp/smarthire-admin-register.out
  if [[ $GENERATED -eq 1 ]]; then
    log "Removing the generated invitation code after the failed attempt"
    write_env AUTH_ADMIN_SIGNUP_CODE ""
    "${COMPOSE[@]}" up -d --no-deps auth-service >/dev/null 2>&1 || true
  fi
  die "Registration failed with HTTP $HTTP_CODE."
fi
rm -f /tmp/smarthire-admin-register.out

umask 077
{
  printf 'SmartHire initial administrator\n'
  printf 'Created:  %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'URL:      https://%s/login\n' "$DOMAIN"
  printf 'Email:    %s\n' "$ADMIN_EMAIL"
  printf 'Password: %s\n' "$ADMIN_PASSWORD"
  printf '\nSign in, then change this password. Delete this file afterwards:\n'
  printf '  shred -u %s\n' "$OUT_FILE"
} > "$OUT_FILE"
chmod 600 "$OUT_FILE"
unset ADMIN_PASSWORD

log "Administrator created. Credentials written to $OUT_FILE (mode 600)."
log "Closing the invitation path and recreating auth-service"
write_env AUTH_ADMIN_SIGNUP_CODE ""
"${COMPOSE[@]}" up -d --no-deps auth-service
wait_for_auth || die "auth-service did not become healthy after closing the invitation code."

log "Done. The invitation code is blank in $ENV_FILE and the service has been recreated."
log "Next: sign in at https://$DOMAIN/login, change the password, then 'shred -u $OUT_FILE'."
