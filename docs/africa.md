# Flick — Africa+ (étape 19)

Couche configurable au-dessus de l'architecture existante : aucun pays
hard-codé (`if country == Cameroon` interdit), aucune duplication des
systèmes (paiement, wallet, modération, IA réutilisés).

## Pays & configuration (`region/`)

| Endpoint | Accès | Source |
|---|---|---|
| `GET /api/countries`, `/countries/{code}` | public | table `countries` (15 marchés seedés) |
| `GET /api/locales`, `/api/currencies` | public | dérivés des pays actifs (jamais inventés) |
| `GET /api/payment-methods?country=XX` | public | `country_payment_methods` (actifs seuls) |
| `GET /api/config/region` | public | flags `features.*` + taux + pays actifs |
| `PUT /api/admin/region/countries/{code}` | ADMIN | activation pays |
| `GET/POST /api/admin/region/countries/{code}/payment-methods` | ADMIN | préparation moyens (`active=false`) |
| `PUT /api/admin/region/payment-methods/{id}` | ADMIN | activation (exige intégration officielle) |
| `GET /api/coin-packages?currency=XAF` | AUTH | vitrine filtrée par devise (montants lus en base) |

Feature flags (défaut = activé, via `PUT /api/admin/config`) :
`features.africa_discovery`, `features.regional_trends`,
`features.mobile_money`, `features.multi_currency`,
`features.data_saver`, `features.multilingual_moderation`.

## Devises & taux

Montants en `BigDecimal`/`DECIMAL` partout (jamais float/double).
`ExchangeRateService` : sans source fiable → `available()=false` et
conversion refusée en 503 (`RATE_UNAVAILABLE`) — aucun taux inventé.
Brancher une source = implémenter `ExchangeRateProvider` (taux datés,
cache Redis 24 h).

## Paiements régionaux

`Payment.Provider` existant réutilisé (WAVE, ORANGE_MONEY, MTN_MOMO,
MPESA, STRIPE, PAYSTACK, FLUTTERWAVE). Seed = **préparation inactive** :
aucune API Mobile Money inventée, aucun webhook simulé. Le sandbox
reste le seul chemin de test ; en prod sans implémentation réelle, le
registre refuse explicitement (503).

## Découverte (`discovery/`)

- `GET /api/discovery/trending[?country&language&category&size]` et
  `/trending/{country}` (404 si code inconnu). Score transparent :
  live = 100 (LIVE) + 2×peak + récence 48 h ; vidéo = 20×ln(1+vues) +
  récence 30 j. Filtre pays = pays **déclaré** du créateur (sous-requête
  EXISTS, pas de GPS, pas de N+1). Cache 2 min.
- `GET /api/discovery/creators/emerging[?country]` : chaînes ACTIVES
  avec ≥ 2 follows sur 30 j (agrégation SQL). Aucune promesse de succès.

## Profils & i18n

- Profil : `countryCode`, `language` (existants) + `timezone` (IANA
  validé) + `preferredCurrency` (ISO-3). Public : jamais timezone/devise.
- Frontend `src/i18n/` (sans dépendance) : `fr` + `en`, résolution
  profil > localStorage > navigateur > `fr`. Navigation traduite ;
  migration progressive documentée (Settings, Profil, Assistant,
  Explorer, player, sélecteurs).
- Horaires : `Instant` UTC côté backend, `Intl` + fuseau profil côté
  frontend (`utils/time.ts`).

## Data saver & player

`DataSaverContext` (persisté) : pas de préchargement, pas d'autoplay,
240p par défaut, pastille ⏳. Player : états waiting/loading/buffering/
reconnecting (3 tentatives backoff) /failed + bouton réessai, menu
qualité 144p–1080p (s'appliquera aux renditions adaptatives ; source
unique lue telle quelle en attendant — aucune fausse promesse).

## Modération multilingue

Règles locales étendues (variantes Mobile Money : momo, orange money,
dépôt…), toujours **recommandation + revue humaine** (jamais de ban
auto sur un mot — slang et langues locales → provider → humain).
Contexte `country`/`language` transmis à l'IA étape 18 comme DONNÉE.

##Images & cache

Vignettes existantes réutilisées ; préchargement coupé en data saver.
Cache ciblé : pays (15 min), tendances (2 min), config flags (lecture
directe, volume faible). Jamais de cache sur données privées, secrets,
tokens.
