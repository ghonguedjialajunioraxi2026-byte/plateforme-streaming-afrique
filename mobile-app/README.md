# FLICK — Application mobile officielle (Flutter)

Client mobile FLICK (spectateurs d'abord) connecté **uniquement** au backend
existant (`../backend`). Aucune logique métier dupliquée : ni auth, ni base,
ni paiement, ni streaming, ni chat bis.

## Prérequis

- Flutter 3.47+ (SDK : `~/sdks/flutter`), Android SDK 36 (`~/Android/Sdk`)
- Backend FLICK démarré (voir `../backend/docker-compose.yml` + profil `dev`)

## Lancement

```bash
cd mobile-app
flutter pub get

# Émulateur Android (10.0.2.2 = localhost du PC hôte) :
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api

# Appareil physique (même Wi-Fi, IP LAN du PC) :
flutter run --dart-define=API_BASE_URL=http://192.168.1.20:8080/api \
  --dart-define=HLS_BASE_URL=http://192.168.1.20:18080
```

Voir `.env.example` pour toutes les variables (`FLICK_ENV`, `PUBLIC_SITE_URL`…).
Aucune clé secrète : ni dans Git, ni dans l'APK.

## Connexion Google (« Continuer avec Google »)

Même contrat que le web : l'app obtient un id_token OIDC, le backend le
vérifie (signature, audience, expiration) puis remet le JWT FLICK
(`POST /api/auth/google`, jamais de secret côté mobile).

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api \
  --dart-define=GOOGLE_SERVER_CLIENT_ID=xxx.apps.googleusercontent.com
```

1. Console Google Cloud (**même projet que le backend**) : écran de
   consentement + client OAuth **Web** (c'est son ID ci-dessus, identique
   à `GOOGLE_CLIENT_ID` backend) + client OAuth **Android**
   (package `com.flick.flick`, SHA-1 du keystore debug/release) +
   client OAuth **iOS** (bundle `com.flick.flick`).
2. iOS : remplacer `REMPLACER-PAR-CLIENT-ID-INVERSE` dans
   `ios/Runner/Info.plist` par le `REVERSED_CLIENT_ID` du client iOS.
3. Android : aucune clé dans l'APK ; Play Services requis sur l'appareil.
4. Sans `GOOGLE_SERVER_CLIENT_ID` : bouton désactivé avec mention (jamais
   d'appel voué à l'échec) ; backend sans `GOOGLE_CLIENT_ID` : 503 explicite.
5. Email déjà pris par un compte **local** → 409 sans fusion : l'utilisateur
   se connecte par mot de passe (la liaison se fait ensuite côté paramètres).

## Vérifications

```bash
flutter analyze   # 0 problème
flutter test      # unitaires + widget (MockClient, pas de réseau)
flutter build apk --debug   # APK de test (release = keystore requis, voir §)
```

## Architecture

```
lib/
├── main.dart / app.dart / app_shell.dart   # entrée, providers, bottom nav
├── core/
│   ├── config/env.dart          # --dart-define (dev/staging/prod)
│   ├── constants/               # branding, chemins API (= backend)
│   ├── theme/                   # FlickColors, FlickTheme, spacing/radius/shadows
│   ├── network/                 # ApiClient (enveloppe, refresh single-flight)
│   ├── storage/                 # SecureTokenStore (Keystore/Keychain), AppPrefs
│   ├── connectivity/            # online/offline
│   ├── realtime/                # STOMP /ws existant
│   └── utils/                   # format, validators, share
├── features/                    # auth, home, explore, categories, search,
│                                # live (+player/chat/cadeaux), channel, video,
│                                # clip, notifications, messages, profile,
│                                # wallet, subscriptions, settings, assistant
├── shared/                      # models, widgets (Flick*), utils
└── l10n/app_strings.dart        # FR + EN (extensible)
```

**State management : Provider/ChangeNotifier** — choix unique et assumé :
zéro codegen, boilerplate minimal, suffisant pour ce périmètre, respectueux
des appareils modestes (rebuilds ciblés via `watch`/`Consumer`).

## API backend utilisées (toutes existantes, vérifiées)

Auth : `POST /api/auth/{register,login,refresh,logout}`, `GET /api/auth/me`
Découverte : `GET /api/discovery/trending[/{country}]`,
`GET /api/discovery/creators/emerging`, `GET /api/stats/live-streams-count`
Contenus : `GET /api/streams/{id}`, `GET /api/videos/{feed,{id},/playback,/view}`,
`GET /api/clips/{feed,{id},/playback,/view}`
Chaînes : `GET /api/creators/slug/{slug}`, `/api/creators/{id}/{videos,clips}`
Follows : `POST|DELETE /api/follows`, `GET /api/follows/is-following`,
`GET /api/follows/{id}/following`
Profil : `GET|PUT /api/profiles/me`, `GET /api/profiles/username/{u}`
Recherche serveur : `GET /api/{streams,creators,videos/feed,clips/feed}?search=…`
Notifs : `GET /api/notifications?recipientId=…`,
`GET /api/notifications/unread-count?recipientId=…`,
`PATCH /api/notifications/{id}/read` (destinataire uniquement, 403 sinon)
Wallet/paiements : `GET /api/wallet/{me,transactions}`, `GET /api/coin-packages`,
`GET /api/payments/{mine,providers}`
Région : `GET /api/{countries,locales,currencies}`, `GET /api/config/region`
IA : `POST /api/ai/assistant` (clé serveur uniquement)
Temps réel : STOMP `/ws`, topics `/topic/events/*` ; chat via
`POST /api/events/publish` (transport démo PUBLIC du backend).

## Endpoints manquants identifiés (non créés, pas de doublon)

- `POST /api/streams/{id}/gifts` + `GET /api/gifts` (catalogue/prix serveur)
  → `GiftSelector` affiche solde + packs réels en attendant.
- REST messages privés → `MessagesScreen` en état assumé.
- Création de live mobile → feuille « Créer » (OBS/PC prioritaire).

## Branding

`assets/branding/FLICK.png` = source unique (copie du fichier officiel).
Icône launcher + splash natif générés depuis ce fichier
(`flutter_launcher_icons`, `flutter_native_splash`).

## Sécurité

Access token en mémoire uniquement, refresh en secure storage, rotation,
single-flight, logout propre, zéro boucle. Prix/soldes/rôles : serveur seul.
Aucun `userId`/`creatorId` propriétaire imposé par le client (403 serveur).

## Release Android

`flutter build apk --release` exige un keystore (non fourni, non commité) :
créer `android/key.properties` + keystore local, **jamais dans Git**.
iOS : config (nom, icône, scheme `flick://`) prête, build Xcode non testé
sur Linux (honnêteté : à compiler sur macOS).
