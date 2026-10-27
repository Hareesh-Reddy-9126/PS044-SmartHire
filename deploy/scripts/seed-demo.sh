#!/usr/bin/env bash
# Seeds a fresh deployment with a demo recruiter and a handful of published jobs.
#
# Usage (on the VM, from the checkout at /opt/smarthire):
#   sudo ./deploy/scripts/seed-demo.sh
#
# The script is a no-op when the job board already has postings, so it is safe to run after every
# deploy. Public registration is open to recruiters anyway, so the demo account adds no new access;
# its generated password is written to /root/smarthire-demo-credentials.txt (mode 600).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/deploy/.env}"
OUT_FILE="${OUT_FILE:-/root/smarthire-demo-credentials.txt}"

log() { printf '\n[seed-demo] %s\n' "$*"; }
die() { printf '[seed-demo] ERROR: %s\n' "$*" >&2; exit 1; }

[[ ${EUID} -eq 0 ]] || die "Run with sudo so the credential file can be written to /root."
[[ -f "$ENV_FILE" ]] || die "$ENV_FILE not found."

DOMAIN="$(sed -n 's/^DOMAIN=//p' "$ENV_FILE" | head -n 1)"
[[ -n "$DOMAIN" ]] || die "DOMAIN is empty in $ENV_FILE."

API="https://$DOMAIN/api/v1"
PASSWORD="$(openssl rand -base64 32 | tr -dc 'A-Za-z0-9' | cut -c1-24)"

json_field() {
  # Extracts a top-level string/number field from a single-line JSON response.
  sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -n 1
}

log "Checking the public job board"
BOARD="$(curl -sS --max-time 30 "$API/jobs" || true)"
TOTAL="$(printf '%s' "$BOARD" | json_field totalElements)"
TOTAL="${TOTAL:-0}"
if [[ "$TOTAL" -gt 0 ]]; then
  log "Job board already has $TOTAL posting(s); nothing to seed."
  exit 0
fi

EMAIL="demo.recruiter@smarthire.example"
log "Registering demo recruiter $EMAIL"
BODY="$(printf '{"email":"%s","password":"%s","role":"RECRUITER","organizationName":"SmartHire Demo"}' "$EMAIL" "$PASSWORD")"
CODE="$(printf '%s' "$BODY" | curl -sS --max-time 30 -o /tmp/smarthire-seed.out -w '%{http_code}' \
  -H 'Content-Type: application/json' --data-binary @- "$API/auth/register")"

if [[ "$CODE" != "200" && "$CODE" != "201" ]]; then
  # A previous run may have created the account without publishing anything; use a unique address.
  EMAIL="demo.recruiter+$(date -u +%s)@smarthire.example"
  log "Default demo address is taken; retrying as $EMAIL"
  BODY="$(printf '{"email":"%s","password":"%s","role":"RECRUITER","organizationName":"SmartHire Demo"}' "$EMAIL" "$PASSWORD")"
  CODE="$(printf '%s' "$BODY" | curl -sS --max-time 30 -o /tmp/smarthire-seed.out -w '%{http_code}' \
    -H 'Content-Type: application/json' --data-binary @- "$API/auth/register")"
fi
rm -f /tmp/smarthire-seed.out
[[ "$CODE" == "200" || "$CODE" == "201" ]] || die "Recruiter registration failed with HTTP $CODE."

LOGIN="$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD" \
  | curl -sS --max-time 30 -H 'Content-Type: application/json' --data-binary @- "$API/auth/login")"
TOKEN="$(printf '%s' "$LOGIN" | json_field accessToken)"
[[ -n "$TOKEN" ]] || die "Recruiter login did not return an access token."

create_job() {
  local company="$1" role="$2" location="$3"
  local payload created job_id status
  payload="$(printf '{"companyName":"%s","role":"%s","location":"%s"}' "$company" "$role" "$location")"
  created="$(printf '%s' "$payload" | curl -sS --max-time 30 -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' --data-binary @- "$API/jobs")"
  job_id="$(printf '%s' "$created" | json_field jobId)"
  [[ -n "$job_id" ]] || { printf '[seed-demo] Could not create "%s": %s\n' "$role" "$created" >&2; return 1; }
  status="$(curl -sS --max-time 30 -o /dev/null -w '%{http_code}' -X POST \
    -H "Authorization: Bearer $TOKEN" "$API/jobs/$job_id/open")"
  [[ "$status" == "200" || "$status" == "204" ]] || { printf '[seed-demo] Could not publish "%s" (HTTP %s)\n' "$role" "$status" >&2; return 1; }
  printf '[seed-demo] Published %s — %s (%s)\n' "$role" "$company" "$location"
}

log "Publishing demo jobs"
create_job 'Northwind Labs'   'Senior Backend Engineer'    'Bengaluru, India'
create_job 'Northwind Labs'   'Frontend Engineer (React)'  'Remote, India'
create_job 'Helios Analytics' 'Data Engineer'              'Hyderabad, India'
create_job 'Helios Analytics' 'Product Designer'           'Remote'
create_job 'Vertex Systems'   'DevOps Engineer'            'Pune, India'
create_job 'Vertex Systems'   'QA Automation Engineer'     'Chennai, India'

umask 077
{
  printf 'SmartHire demo recruiter\n'
  printf 'Created:  %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'URL:      https://%s/login\n' "$DOMAIN"
  printf 'Email:    %s\n' "$EMAIL"
  printf 'Password: %s\n' "$PASSWORD"
  printf '\nThis account owns the seeded demo jobs. Change or delete it before real use.\n'
} > "$OUT_FILE"
chmod 600 "$OUT_FILE"

log "Demo data ready. Credentials: $OUT_FILE (mode 600)."
log "Public job board: https://$DOMAIN/jobs"
