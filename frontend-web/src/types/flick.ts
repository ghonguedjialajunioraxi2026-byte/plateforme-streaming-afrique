/** Types du design system immersif FLICK (Twitch x Netflix, Afrique). */

export interface FlickStreamer {
  id: string
  name: string
  slug: string
  avatarHue: number
  verified: boolean
  country: string
  countryCode: string
  followers: number
}

export interface FlickLive {
  id: string
  streamer: FlickStreamer
  title: string
  category: string
  language: string
  viewers: number
  startedAt: string
  tags: string[]
  /** Emoji / visuel de fallback cinématique (vraie miniature via API). */
  artwork: string
}

export interface FlickFeatured extends FlickLive {
  description: string
}

export interface FlickCategory {
  id: string
  slug: string
  name: string
  viewers: number
  lives: number
  artwork: string
  gradient: string
}

export interface FlickCreator extends FlickStreamer {
  headline: string
  liveNow: boolean
  viewers?: number
}

export interface FlickChatMessage {
  id: string
  username: string
  color: string
  message: string
  badge?: 'Diffuseur' | 'Modo' | 'VIP' | 'Abonné' | 'Fondateur'
  isSystem?: boolean
}

export interface FlickCountryTrend {
  country: string
  code: string
  viewers: number
  lives: number
  topCategory: string
}

export type FlickQuality = 'Auto' | '1080p' | '720p' | '480p' | '360p' | '240p'
