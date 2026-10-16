#!/usr/bin/env bash
# ============================================================
# FLICK — sauvegarde PostgreSQL (quotidienne via cron systemd)
# Format custom compressé + rotation (rétention 7 j) + manifest SHA256.
# Aucun secret dans ce script : tout vient de l'environnement.
#
# Usage :
#   DB_HOST=... DB_PORT=... DB_NAME=... DB_USERNAME=... \
#   DB_PASSWORD=... BACKUP_DIR=/srv/flick/backups ./backup-postgres.sh
# Cron recommandé : 0 3 * * * (heure creuse Afrique de l'Ouest).
# ============================================================
set -euo pipefail

: "${DB_HOST:?DB_HOST manquant}"
: "${DB_PORT:=5432}"
: "${DB_NAME:?DB_NAME manquant}"
: "${DB_USERNAME:?DB_USERNAME manquant}"
: "${DB_PASSWORD:?DB_PASSWORD manquant}"
BACKUP_DIR="${BACKUP_DIR:-/srv/flick/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
FILE="$BACKUP_DIR/flick-$STAMP.dump"

export PGPASSWORD="$DB_PASSWORD"
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" \
  -F custom -Z 6 -f "$FILE" "$DB_NAME"
unset PGPASSWORD

sha256sum "$FILE" > "$FILE.sha256"
echo "$STAMP OK $FILE $(du -h "$FILE" | cut -f1)" >> "$BACKUP_DIR/manifest.log"

# Rotation : supprime les sauvegardes + checksums au-delà de la rétention.
find "$BACKUP_DIR" -maxdepth 1 -name 'flick-*.dump' -mtime +"$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -maxdepth 1 -name 'flick-*.dump.sha256' -mtime +"$RETENTION_DAYS" -delete

echo "Sauvegarde OK : $FILE"
