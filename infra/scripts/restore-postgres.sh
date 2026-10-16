#!/usr/bin/env bash
# ============================================================
# FLICK — restauration PostgreSQL (procédure d'urgence / test)
# Restaure un .dump (pg_restore --clean --if-exists) après vérification
# SHA256. JAMAIS sur la prod sans fenêtre de maintenance (voir RESTORE.md).
#
# Test de restauration (obligatoire après chaque changement de backup) :
#   1. créer une base scratch : createdb flick_restore_test
#   2. ./restore-postgres.sh <fichier.dump> flick_restore_test
#   3. vérifier : tables, migrations flyway, dernier utilisateur/paiement
# ============================================================
set -euo pipefail

DUMP="${1:?usage: restore-postgres.sh <fichier.dump> [base_cible]}"
TARGET="${2:-$DB_NAME}"
: "${DB_HOST:?DB_HOST manquant}"
: "${DB_PORT:=5432}"
: "${DB_USERNAME:?DB_USERNAME manquant}"
: "${DB_PASSWORD:?DB_PASSWORD manquant}"

if [ -f "$DUMP.sha256" ]; then
  sha256sum -c "$DUMP.sha256"
else
  echo "AVERTISSEMENT : pas de checksum $DUMP.sha256 — restauration quand même ? [o/N]"
  read -r answer
  [ "$answer" = "o" ] || exit 1
fi

export PGPASSWORD="$DB_PASSWORD"
pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" \
  -d "$TARGET" --clean --if-exists "$DUMP"
unset PGPASSWORD

echo "Restauration OK : $DUMP -> $TARGET"
