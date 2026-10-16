# FLICK — Restauration (RTO ≤ 2 h)

## Test de restauration (procédure validée à l'étape 23)

```bash
createdb flick_restore_test
./infra/scripts/restore-postgres.sh <fichier.dump> flick_restore_test
# Vérifier : \dt (32 tables), flyway_schema_history (24 migrations OK),
# SELECT count(*) FROM users / payments, dernier paiement SUCCEEDED.
dropdb flick_restore_test
```

## Panne DB (prod)

1. Stopper backend (`... stop backend`) — évite les écritures partielles.
2. Restaurer sur la base prod (fenêtre de maintenance annoncée).
3. Redémarrer backend (Flyway valide les checksums, `validate-on-migrate`).
4. smoke.sh + vérifications métier (login, wallet, dernier live/VOD).
5. Post-mortem : cause, données perdues (≤ RPO), correctif.

## Panne Redis / backend / SRS / disque plein

- Redis : redémarrage (AOF rejoue) ; à défaut, repli mémoire auto puis
  reseed compteurs — aucune donnée métier perdue (source = Postgres).
- Backend : `up -d --build` version taguée ; healthcheck `/api/health`.
- SRS : `up -d srs` ; lives en cours à relancer côté créateurs (RTMP
  non repris automatiquement — documenté, pas de fausse promesse).
- Disque plein : rotation logs (`max-size`), purge backups > rétention,
  quotas uploads ; alerte avant saturation (monitoring).
