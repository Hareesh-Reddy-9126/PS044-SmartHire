#!/usr/bin/env bash
# Daily SmartHire PostgreSQL backup.
#
# Usage:
#   ./deploy/scripts/backup-postgres.sh
#
# Environment:
#   ENV_FILE                path to deploy/.env (default deploy/.env)
#   BACKUP_DIR              host backup directory (default deploy/backups)
#   BACKUP_RETENTION_DAYS   local retention, default 7
#   BACKUP_REMOTE           optional remote backup target, e.g. rclone:/bucket/smarthire
#
# The script never reads or prints secret values from deploy/.env; Docker Compose reads it.
set -euo pipefail
set -o pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$REPO_ROOT/deploy/.env}"
BACKUP_DIR="${BACKUP_DIR:-$REPO_ROOT/deploy/backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP_FILE="smarthire-$TIMESTAMP.dump"

mkdir -p "$BACKUP_DIR"
DUMP_PATH="$BACKUP_DIR/$DUMP_FILE"
TMP_PATH="$DUMP_PATH.partial"

# Prefer the production override when it is present; local-only runs can disable this with
# COMPOSE_PRODUCTION=false to use the base compose file only.
COMPOSE_FILES=(-f "$REPO_ROOT/deploy/docker-compose.yml")
if [[ "${COMPOSE_PRODUCTION:-1}" != "0" && -f "$REPO_ROOT/deploy/docker-compose.prod.yml" ]]; then
  COMPOSE_FILES+=(-f "$REPO_ROOT/deploy/docker-compose.prod.yml")
fi

# pg_dump runs inside the Postgres container and reads its credentials from the container env.
docker compose "${COMPOSE_FILES[@]}" --env-file "$ENV_FILE" exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-privileges' \
  > "$TMP_PATH"

# Fail fast if the dump is empty or corrupt.
if [[ ! -s "$TMP_PATH" ]]; then
  rm -f "$TMP_PATH"
  echo "ERROR: produced an empty PostgreSQL dump" >&2
  exit 1
fi

# Integrity check with the matching Postgres client version.
docker run --rm -v "$BACKUP_DIR:/backups:ro" postgres:16-alpine pg_restore \
  --list "/backups/$DUMP_FILE" > /dev/null

mv "$TMP_PATH" "$DUMP_PATH"
sha256sum "$DUMP_PATH" > "$DUMP_PATH.sha256"

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'smarthire-*.dump' -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'smarthire-*.dump.sha256' -mtime "+$RETENTION_DAYS" -delete

if [[ "${BACKUP_REMOTE:-}" != "" ]]; then
  if ! command -v rclone >/dev/null 2>&1; then
    echo "ERROR: BACKUP_REMOTE is set but rclone is not installed" >&2
    exit 1
  fi
  rclone copy "$BACKUP_DIR" "$BACKUP_REMOTE" \
    --include 'smarthire-*.dump' \
    --include 'smarthire-*.dump.sha256'
fi

echo "PostgreSQL backup written: $DUMP_PATH"
