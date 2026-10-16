#!/usr/bin/env bash
# ============================================================
# FLICK — smoke tests post-déploiement (staging/prod, §70)
# Vérifie : proxy, frontend, API, auth (401/200), sécurité de base
# (ingest sans secret → 401). Sortie non-zéro au premier échec.
# Usage : BASE_URL=https://localhost:8443 ./smoke.sh
# ============================================================
set -euo pipefail

BASE_URL="${BASE_URL:-https://localhost:8443}"
CURL="curl -fsS --max-time 15"
[ "${SMOKE_INSECURE:-false}" = "true" ] && CURL="$CURL -k"

pass() { echo "OK   $1"; }
fail() { echo "ECHEC $1" >&2; exit 1; }

# 1. Frontend servi par le proxy.
$CURL "$BASE_URL/" -o /dev/null || fail "frontend /"
pass "frontend /"

# 2. Santé backend via proxy (aucun /actuator exposé publiquement sauf health).
$CURL "$BASE_URL/api/health" | grep -q '"status"' || fail "api/health"
pass "api/health"

# 3. Catalogue public lisible.
$CURL "$BASE_URL/api/categories" -o /dev/null || fail "api/categories"
pass "api/categories"

# 4. Zone protégée sans jeton → 401 (pas 200, pas 500).
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$BASE_URL/api/creators/me" || true)
[ "$CODE" = "401" ] || fail "creators/me anonyme (attendu 401, obtenu $CODE)"
pass "creators/me anonyme → 401"

# 5. Notifications d'autrui sans jeton → 401 (SEC-001).
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$BASE_URL/api/notifications?recipientId=1" || true)
[ "$CODE" = "401" ] || fail "notifications anonyme (attendu 401, obtenu $CODE)"
pass "notifications anonyme → 401"

# 6. Hook ingest sans secret → 401 (pas d'ingest sauvage).
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 -X POST "$BASE_URL/api/ingest/on-publish" || true)
[ "$CODE" = "401" ] || fail "ingest sans secret (attendu 401, obtenu $CODE)"
pass "ingest sans secret → 401"

# 7. Actuator interne NON exposé publiquement.
CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 15 "$BASE_URL/actuator/prometheus" || true)
[ "$CODE" = "404" ] || fail "actuator public (attendu 404, obtenu $CODE)"
pass "actuator non exposé → 404"

echo "SMOKE OK : $BASE_URL"
