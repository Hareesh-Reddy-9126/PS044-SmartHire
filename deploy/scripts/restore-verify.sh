#!/usr/bin/env bash
# Verifies that a SmartHire PostgreSQL dump is restorable, without touching the production
# database. It spins up a disposable Postgres container, restores the dump inside it, runs a few
# sanity queries, then removes the container.
#
# Usage:
#   ./deploy/scripts/restore-verify.sh [optional-smarthire-YYYY-....dump]
#
# Environment:
#   BACKUP_DIR   host backup directory (default deploy/backups)
#   ENV_FILE     path to deploy/.env (default deploy/.env). Only needed to resolve compose files.
set -euo pipefail
set -o pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/deploy/.env}"
BACKUP_DIR="${BACKUP_DIR:-$REPO_ROOT/deploy/backups}"

if [[ $# -ge 1 ]]; then
  DUMP_FILE="$1"
else
  DUMP_FILE="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name 'smarthire-*.dump' -printf '%T@ %f\n' \
    | sort -nr | head -n 1 | cut -d' ' -f2-)"
fi

if [[ -z "$DUMP_FILE" || ! -f "$BACKUP_DIR/$DUMP_FILE" ]]; then
  echo "ERROR: no PostgreSQL dump found in $BACKUP_DIR" >&2
  exit 1
fi

# Uses a temporary, disposable Postgres container. The production containers are not touched, and
# no host port is published, so there is no chance of accidentally connecting to a live database.
COMPOSE_FILES=(-f "$REPO_ROOT/deploy/docker-compose.yml")
if [[ -f "$REPO_ROOT/deploy/docker-compose.prod.yml" ]]; then
  COMPOSE_FILES+=(-f "$REPO_ROOT/deploy/docker-compose.prod.yml")
fi

# `docker compose config` is only used to validate that the compose environment is available before
# starting a temporary restore container.
if [[ -f "$ENV_FILE" ]]; then
  docker compose "${COMPOSE_FILES[@]}" --env-file "$ENV_FILE" config --quiet >/dev/null
fi

CONTAINER_NAME="smarthire-restore-verify-$RANDOM"
docker run --rm -d --name "$CONTAINER_NAME" \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -v "$BACKUP_DIR:/backups:ro" \
  postgres:16-alpine > /dev/null
trap 'docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true' EXIT

for _ in $(seq 1 60); do
  if docker inspect --format '{{.State.Health.Status}}' "$CONTAINER_NAME" | grep -q '^healthy$'; then
    break
  fi
  sleep 1
done
if [[ "$(docker inspect --format '{{.State.Health.Status}}' "$CONTAINER_NAME")" != "healthy" ]]; then
  echo "ERROR: temporary restore container did not become healthy" >&2
  exit 1
fi

docker exec "$CONTAINER_NAME" pg_restore \
  -U postgres -d postgres --no-owner --no-privileges "/backups/$DUMP_FILE"

TABLE_COUNT="$(docker exec "$CONTAINER_NAME" psql -U postgres -d postgres -tAc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema IN ('auth_svc','job_svc','application_svc','recruitment_svc','document_svc','notification_svc','ai_svc');")"

if [[ "$TABLE_COUNT" -lt 7 ]]; then
  echo "ERROR: restore verification only found $TABLE_COUNT of 7 expected schema tables" >&2
  exit 1
fi

docker rm -f "$CONTAINER_NAME" >/dev/null
trap - EXIT

echo "Restore verification succeeded for $DUMP_FILE"
