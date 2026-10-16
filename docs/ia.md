# Flick — Intelligence artificielle (étape 18)

## Architecture

```text
React → /api/ai/* → AiService → AIProvider → modèle
```

L'application ne dépend que de `AIProvider` (`backend/.../ai/provider/`).
Deux implémentations :

| Provider | Quand | Coût / données |
|---|---|---|
| `stub` (défaut) | `AI_PROVIDER=stub` ou pas de `AI_BASE_URL` | 0, 100 % local, déterministe |
| `http` | `AI_PROVIDER=http` + `AI_BASE_URL` + `AI_API_KEY` | OpenAI-compatible (OpenAI, Ollama…) |

## Variables d'environnement

Voir `.env.example` (préfixe `AI_*`). La clé API ne vit QUE dans
l'environnement serveur — jamais dans Git, jamais dans le frontend.

## Endpoints

| Route | Rôle | Usage |
|---|---|---|
| `POST /api/ai/creator/titles` | CREATOR | 3 titres proposés |
| `POST /api/ai/creator/descriptions` | CREATOR | rédiger / améliorer |
| `POST /api/ai/creator/tags` | CREATOR | 3–8 tags |
| `GET /api/ai/creator/vod/{id}/summary` | CREATOR | résumé métadonnées (VOD du demandeur) |
| `GET /api/ai/creator/vod/{id}/clip-suggestions` | CREATOR | tranches indicatives (transcription requise pour la vraie détection) |
| `POST /api/ai/creator/analytics` | CREATOR | chiffres réels, insuffisance signalée |
| `POST /api/ai/assistant` | AUTHENTICATED | aide officielle (base `FlickKnowledge`) |
| `POST /api/ai/search-assist` | AUTHENTICATED | intention → moteur de recherche existant |
| `POST /api/ai/moderation/classify` | MODERATOR | SAFE/SUSPICIOUS/VIOLATION + confiance (recommandation, jamais de sanction auto) |
| `GET /api/ai/admin/usage` | ADMIN | requêtes/tokens/latence/erreurs par feature (7 j) |
| `GET /api/ai/admin/features` | ADMIN | états `ai.features.<nom>` |

## Garde-fous

- Assainissement systématique (JWT, clés, secrets retirés ; prompt borné).
- Rate limit / minute + quota / jour / rôle (configurables, `ai.*`).
- Usage enregistré dans `ai_usage` (compteurs, jamais le contenu des prompts).
- Feature flags admin : `PUT /api/admin/config` avec `ai.features.<nom> = false`.
- Modération hybride : règles locales FR/EN d'abord, provider ensuite,
  revue humaine toujours requise avant toute sanction.
- L'IA n'invente jamais : analytics insuffisants → signalé ; VOD sans
  transcription → résumé métadonnées + mention explicite.
- Conversations d'assistant : NON persistées par défaut (tables
  `ai_conversation` / `ai_message` prêtes pour un historique opt-in, supprimable).
