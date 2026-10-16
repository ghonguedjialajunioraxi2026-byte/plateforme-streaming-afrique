# FLICK — Sauvegardes

## PostgreSQL (quotidien, `infra/scripts/backup-postgres.sh`)

- `pg_dump -F custom -Z 6` + SHA256 + `manifest.log`, rétention 7 j,
  cron 03:00 UTC. Secrets par environnement uniquement.
- Stockage : volume `pgdata` + copie DISTANTE (le backup sur le même
  disque ne protège pas d'une perte serveur — `rsync`/`rclone` vers un
  stockage séparé, chiffré au repos si données sensibles).
- Uploads (`uploads/`) : `rsync -a --delete` quotidien vers le même
  stockage distant (VOD/clips/avatars) ; Redis : AOF persisté, pas de
  backup dédié (reconstructible, jamais source de vérité).

## Vérification

- Checksum à chaque backup ; alerte si backup absent > 26 h ou taille
  anormale (±50 % vs médiane 7 j).
- Restauration TESTÉE après chaque changement (voir RESTORE.md) —
  un backup non testé n'est pas fiable. RPO ≤ 24 h.
