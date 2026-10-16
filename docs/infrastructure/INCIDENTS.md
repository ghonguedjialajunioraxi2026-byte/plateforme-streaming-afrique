# FLICK — Réponse aux incidents

## Triage (5 min)

`docker compose ps` + `logs --tail` (proxy/backend/srs) + `/api/health`
+ Prometheus (`BackendDown`, `High5xxRate`) : classer (infra / app /
sécurité), estimer l'impact (auth ? paiements ? lives ?), décider
rollback vs correctif avant.

## Procédures

- **Backend down** : `up -d backend` version taguée ; si échec au boot,
  lire le fail-fast (secret manquant ? migration ?) ; rollback si
  régression (ROLLBACK.md).
- **DB down** : vérifier disque/connexions (`pg_stat_activity`,
  locks) ; redémarrer postgres ; si corruption → RESTORE.md.
- **Redis down** : redémarrer ; l'app dégrade (repli mémoire) —
  surveiller les 429 et la présence.
- **SRS down / ingest refusé** : logs SRS + hook backend (401 secret ?
  403 statut/clé ?) ; créateurs : recouper clé + statut LIVE.
- **Paiements** : webhooks 4xx/5xx, `ALREADY_PROCESSED` normal en
  rejeu ; jamais de crédit manuel sans réconciliation (statuts
  provider vs `payments`).
- **Certificat expiré** : renouveler (DEPLOY.md §4), recharger nginx.
- **Fuite de secret** : rotater IMMÉDIATEMENT (env + redéploiement),
  révoquer côté provider, auditer les logs d'accès, post-mortem.
  Rotation documentée sans jamais afficher les valeurs.
- **Attaque (force-brute, spam)** : resserrer `RATE_LIMIT_*`,
  `RATE_LIMIT_TRUST_PROXY=false` si XFF forgé en direct, bans via
  modération, blocage IP au firewall en dernier ressort.

## Post-incident

Cause racine, chronologie, données impactées, correctif + test de
non-régression, mise à jour de ce runbook.
