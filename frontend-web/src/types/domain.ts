/// Types de domaine — le cœur des données manipulées par l'application.
/// Ces types reflètent ce que le backend exposera via l'API.

export type Role = 'USER' | 'CREATOR' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN'

export interface User {
  id: string
  username: string
  email: string
  displayName?: string
  avatarUrl?: string
  bannerUrl?: string
  bio?: string
  country?: string
  language?: string
  roles: Role[]
  createdAt: string
}

export interface Channel {
  id: string
  userId: string
  name: string
  slug: string
  description?: string
  followersCount: number
  isLive: boolean
}

/** Profil public (étape 09) — miroir de `PublicProfileDto` backend. */
export interface PublicProfile {
  username: string
  displayName: string
  avatarUrl: string | null
  bio: string | null
  countryCode: string | null
  language: string | null
  websiteUrl: string | null
  xHandle: string | null
  instagramHandle: string | null
  youtubeUrl: string | null
  createdAt: string
}

/** Profil connecté (étape 09) — miroir de `ProfileDto` backend. */
export interface MyProfile extends PublicProfile {
  userId: number
  email: string
  emailVerified: boolean
  birthDate: string | null
  notifyLive: boolean
  notifyContent: boolean
  isPublic: boolean
  timezone: string | null
  preferredCurrency: string | null
}

/** Référence légère de catégorie (chaîne). */
export interface CategoryRef {
  id: number
  slug: string
  name: string
}

/** Fiche chaîne détaillée (étape 09) — miroir de `CreatorProfileDto`. */
export interface ChannelDetail {
  id: number
  displayName: string
  slug: string
  bio: string | null
  avatarUrl: string | null
  bannerUrl: string | null
  userId: number
  createdAt: string
  followerCount: number
  /** Absent (`undefined`) quand le backend ne le renseigne pas. */
  primaryCategory?: CategoryRef | null
  status: 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
}

export type StreamStatus = 'UPCOMING' | 'LIVE' | 'OFFLINE' | 'ENDED'

export interface Stream {
  id: string
  channelId: string
  title: string
  category: string
  viewersCount: number
  status: StreamStatus
  thumbnailUrl?: string
  language: string
  startedAt?: string
}

export interface Video {
  id: string
  channelId: string
  title: string
  description?: string
  thumbnailUrl?: string
  durationSeconds: number
  viewsCount: number
  createdAt: string
}

export interface Clip {
  id: string
  channelId: string
  title: string
  thumbnailUrl?: string
  viewsCount: number
  createdAt: string
}

export interface Category {
  id: string
  name: string
  slug: string
  viewersCount: number
  streamsCount: number
  thumbnailUrl?: string
}

/** Message de chat temps réel (WebSocket). */
export interface ChatMessage {
  id: string
  streamId: string
  userId: string
  username: string
  content: string
  createdAt: string
  badges?: string[]
}