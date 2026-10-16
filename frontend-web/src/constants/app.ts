/// Identité officielle de la plateforme : FLICK.
/// Marque affichée à l'utilisateur final dans toute l'interface.
export const APP_NAME = 'FLICK'
export const APP_TAGLINE = 'Streaming en direct'
export const APP_DESCRIPTION =
  'FLICK est une plateforme de streaming en direct pensée pour les créateurs et les communautés africaines.'
export const APP_TITLE_SUFFIX = 'FLICK — Streaming en direct'

/// URL publique du site (canonical + Open Graph, étape 25).
/// Placeholder à remplacer au déploiement via VITE_SITE_URL (jamais de secret).
export const SITE_URL =
  import.meta.env.VITE_SITE_URL ?? 'https://app.flick.example'
