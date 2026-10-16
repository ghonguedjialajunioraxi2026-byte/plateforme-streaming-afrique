# FLICK — Programme de bêta privée (étape 24, version beta-0.1.0)

## Principe

Bêta **privée et contrôlée** : invitation obligatoire (environnement
bêta), aucun recrutement public, aucune statistique gonflée, paiements
en sandbox uniquement. Mobile distribué en interne (pas de store public).

## Environnement

Staging Compose (`infra/docker-compose.prod.yml` +
`infra/docker-compose.staging.yml`) = environnement bêta : base, Redis,
stockage, secrets et monitoring dédiés, jamais mélangés avec dev.
Activation : `BETA_INVITE_ONLY=true` (+ `BETA_INVITATION_TTL`, défaut
14 j). Secrets de recette forts et distincts de la prod.

## Accès (UN seul mécanisme)

Invitation ADMIN (`/admin/beta`) : email + note + code montré **une
fois**. Code lié à l'email (incessible), usage unique, expiration,
révocation. Inscription sans code valide → 403. Suspension/retrait :
statuts existants (`SUSPENDED`/`CLOSED` via `/admin/users`) — aucun
statut dupliqué. Profils testeurs (viewer/créateur/modo) = catégories
documentaires (note d'invitation), pas des rôles.

## Onboarding

Parcours court : `/beta` (conditions) → inscription + code → connexion
→ profil/avatar → explorer → premier live → follow → chat. Pas de
questionnaire imposé ; centres d'intérêt via catégories existantes.

## Feedback et triage

`/feedback` (catégorie + titre + description ; contexte non sensible :
route, version, appareil). Workflow : OPEN → TRIAGED → IN_PROGRESS →
FIXED → VERIFIED → CLOSED ; priorité P0..P3 posée par l'équipe.
P0 = plateforme inaccessible, login global impossible, danger
paiement, perte de données, stream global impossible.
Procédure : réception → reproduction → validation → classification →
priorité → correction → test → staging → vérification → fermeture.

## Consentement et données

Page `/beta` + mention sur `/feedback` : seules les données affichées
sont envoyées. Pas de localisation précise, pas de contenu privé.
Comptes supprimables (fonction existante) ; données financières/audit
conservées.

## Mesures (définitions)

- Activé : onboarding terminé + ≥ 1 live regardé.
- Rétention D1/D7/D14/D30 quand les volumes le permettront (ne rien
  inventer avant).
- Funnel : visite → inscription → activation → 1er live → follow →
  retour. Créateur : chaîne → config → 1er live → 1er spectateur →
  fin → VOD.
- Interdits : viewers/followers/revenus gonflés ; toute stat marquée
  RÉELLE / TEST / SIMULÉE selon son origine.

## Critères de sortie (vers bêta publique)

Zéro P0 ouvert, zéro faille critique ouverte, auth/API/streaming/chat/
VOD/clips/wallet/sandbox stables, dashboard/admin/mobile stables,
monitoring + backup vérifiés, feedback collecté et trié, rollback
testé, documentation à jour. Seuils chiffrés uniquement sur données
réelles.
