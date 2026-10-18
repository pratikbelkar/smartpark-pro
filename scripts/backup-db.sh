#!/usr/bin/env bash
# Nightly-style PostgreSQL backup. Usage:
#   POSTGRES_USER=... POSTGRES_DB=... ./scripts/backup-db.sh [output-dir]
# Keeps 14 days of compressed dumps. Restore with:
#   pg_restore -U <user> -d <db> smartpark-YYYYMMDD.dump
set -euo pipefail

OUT_DIR="${1:-./backups}"
HOST="${POSTGRES_HOST:-localhost}"
PORT="${POSTGRES_PORT:-5433}"
DB="${POSTGRES_DB:-smartpark}"
USER="${POSTGRES_USER:-smartpark}"

mkdir -p "$OUT_DIR"
FILE="$OUT_DIR/smartpark-$(date +%Y%m%d).dump"
pg_dump -h "$HOST" -p "$PORT" -U "$USER" -d "$DB" -F c -f "$FILE"
gzip -f "$FILE"
find "$OUT_DIR" -name 'smartpark-*.dump.gz' -mtime +14 -delete
echo "Backup written to $FILE.gz"
