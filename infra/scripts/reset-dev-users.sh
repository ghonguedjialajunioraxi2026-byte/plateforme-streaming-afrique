#!/usr/bin/env bash
# ============================================================
# FLICK — reset des DONNÉES UTILISATEURS (DEV / BÊTA uniquement).
#
# Vide les tables liées aux utilisateurs pour préparer le PREMIER
# compte réel. Conserve : schéma, migrations, index, contraintes et
# données de référence (catégories, pays, coin packages, config).
#
# SÉCURITÉ :
# - REFUSE de tourner si SPRING_PROFILES_ACTIVE=prod ou si
#   FLICK_RESET_I_UNDERSTAND=PROD est absent pour toute cible
#   autre que localhost ;
# - exige une sauvegarde récente (BACKUP_DIR + manifest.log) sauf
#   --skip-backup-check explicite ;
# - demande confirmation interactive sauf --yes.
#
# Usage :
#   DB_HOST=localhost DB_PORT=5432 DB_NAME=flick DB_USERNAME=flick \
#   DB_PASSWORD=xxx ./reset-dev-users.sh [--yes] [--skip-backup-check]
# ============================================================
set -euo pipefail

YES=0
SKIP_BACKUP_CHECK=0
for arg in "$@"; do
  case "$arg" in
    --yes) YES=1 ;;
    --skip-backup-check) SKIP_BACKUP_CHECK=1 ;;
    *) echo "Option inconnue : $arg" >&2; exit 2 ;;
  esac
done

: "${DB_HOST:?DB_HOST manquant}"
: "${DB_PORT:=5432}"
: "${DB_NAME:?DB_NAME manquant}"
: "${DB_USERNAME:?DB_USERNAME manquant}"
: "${DB_PASSWORD:?DB_PASSWORD manquant}"

if [[ "${SPRING_PROFILES_ACTIVE:-dev}" == "prod" ]]; then
  echo "REFUS : SPRING_PROFILES_ACTIVE=prod — reset interdit en production." >&2
  echo "En production, seul un DBA nommé peut purger, hors de ce script." >&2
  exit 1
fi

if [[ "$DB_HOST" != "localhost" && "$DB_HOST" != "127.0.0.1" && "${FLICK_RESET_I_UNDERSTAND:-}" != "DEV-HOST:$DB_HOST" ]]; then
  echo "REFUS : DB_HOST=$DB_HOST n'est pas local." >&2
  echo "Pour une base distante de DEV, exportez FLICK_RESET_I_UNDERSTAND=\"DEV-HOST:$DB_HOST\"." >&2
  exit 1
fi

if [[ "$SKIP_BACKUP_CHECK" -eq 0 ]]; then
  BACKUP_DIR="${BACKUP_DIR:-/srv/flick/backups}"
  if ! ls -t "$BACKUP_DIR"/flick-*.dump >/dev/null 2>&1; then
    echo "Aucune sauvegarde trouvée dans $BACKUP_DIR." >&2
    echo "Faites d'abord ./backup-postgres.sh (ou relancez avec --skip-backup-check)." >&2
    exit 1
  fi
  echo "Sauvegarde trouvée : $(ls -t "$BACKUP_DIR"/flick-*.dump | head -1)"
fi

# Tables de DONNÉES utilisateurs (référence conservée :
# categories, countries, country_payment_methods, coin_packages,
# subscription_plans, gift_items, currencies, app_config).
TABLES="auth_sessions email_tokens profiles creator_profiles streams videos clips follows subscriptions chat_messages notifications moderation_reports moderation_actions wallets transactions gift_sends payments payment_refunds stat_snapshots admin_audit_log beta_feedback beta_invitations ai_usage ai_conversation ai_message users"

if [[ "$YES" -eq 0 ]]; then
  echo "Base cible : $DB_USERNAME@$DB_HOST:$DB_PORT/$DB_NAME"
  echo "Tables purgées : $TABLES"
  read -r -p "Purger ces tables ? (tapez OUI pour confirmer) " answer
  if [[ "$answer" != "OUI" ]]; then
    echo "Abandon."
    exit 0
  fi
fi

export PGPASSWORD="$DB_PASSWORD"
BEFORE="$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -tAc "SELECT count(*) FROM users;" || echo "?")"
# Boucle PL/pgSQL : ne purge que les tables EXISTANTES (base à jour ou non),
# puis repart les séquences à 1. CASCADE pour les FK.
TABLES_SQL="$(printf "'%s'," $TABLES)"
TABLES_SQL="ARRAY[${TABLES_SQL%,}]"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" -v ON_ERROR_STOP=1 <<'SQL'
DO $$
DECLARE
  t text;
  tables text[] := ARRAY['auth_sessions','email_tokens','profiles','creator_profiles','streams','videos','clips','follows','subscriptions','chat_messages','notifications','moderation_reports','moderation_actions','wallets','transactions','gift_sends','payments','payment_refunds','stat_snapshots','admin_audit_log','beta_feedback','beta_invitations','ai_usage','ai_conversation','ai_message','users'];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    IF to_regclass('public.' || t) IS NOT NULL THEN
      EXECUTE format('TRUNCATE public.%I RESTART IDENTITY CASCADE', t);
    ELSE
      RAISE NOTICE 'Table absente, ignorée : %', t;
    END IF;
  END LOOP;
END $$;
SQL
unset PGPASSWORD

echo "Reset OK : users avant=$BEFORE, après=0 attendu."
