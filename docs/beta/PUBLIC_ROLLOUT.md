# FLICK — Ouverture bêta publique contrôlée (étape 25)

## Pré-requis GO (tous exigés)

- TLS actif + `CORS_ALLOWED_ORIGINS`/`VITE_API_URL`/`VITE_SITE_URL` réels.
- SMTP renseigné et testé (inscription + récupération de bout en bout).
- `BETA_INVITE_ONLY=false`, sandbox paiements actif, secrets ≠ recette.
- Backup récent + restore testé, rollback connu, smoke.sh vert.
- Aucune P0 / faille critique ouverte.

## Phases

- **A** (1–2 j) : petit trafic (liens directs, pas d'annonce). Seuils :
  5xx < 1 %, p95 < 1,5 s, 0 incident auth/paiement.
- **B** : x5, communautés partenaires. Mêmes seuils + rétention D1.
- **C** : annonce blog/réseaux, inscriptions ouvertes, CAPTCHA si abus.
- **D** : bêta complète, revues quotidiennes.

À chaque phase : STOP + ROLLBACK si P0, fuite de données, double
crédit, auth cassée, streaming/chat globalement inutilisables.

## Surveillance (premières heures/jour/semaine)

Inscriptions, erreurs 4xx/5xx, latence p95, WebSocket, lives/viewers,
webhooks, DB (pool/locks), Redis, disque, modération, feedback.
Alertes Prometheus existantes + seuils phases ci-dessus.

## Rapport quotidien (structure)

Date, utilisateurs (nouveaux/actifs), créateurs, lives, viewers,
messages, follows, abonnements, paiements sandbox, erreurs, incidents,
rapports, feedback (top problèmes), décision (poursuivre/ralentir/
rollback).

## Feature flags existants (pas de nouveau système)

`AppConfig` versionné (`/admin/settings`, rollback trivial) +
`RegionService.featureEnabled()` / `AiService` (fail-open loggé).
Usages : IA, découverte régionale, paiements — désactivation sans
redéploiement en cas d'incident.

## Support et communication

`/support` + `/feedback` + `/statut` + `/beta`. Annonce type :
« FLICK est en bêta publique : certaines fonctions évoluent, signalez
tout problème via Retour bêta ». Incidents : heure, service, cause,
impact, durée, résolution, prévention (post-mortem).
