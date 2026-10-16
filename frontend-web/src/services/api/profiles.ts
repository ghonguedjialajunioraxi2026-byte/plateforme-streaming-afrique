/// Service profils (étape 09) — miroir de `ProfileController` backend.
import { http } from '../http'
import type { MyProfile, PublicProfile } from '../../types/domain'

export interface UpdateProfileInput {
  displayName?: string
  bio?: string | null
  countryCode?: string | null
  language?: string | null
  timezone?: string | null
  preferredCurrency?: string | null
  websiteUrl?: string | null
  xHandle?: string | null
  instagramHandle?: string | null
  youtubeUrl?: string | null
  birthDate?: string | null
  notifyLive?: boolean
  notifyContent?: boolean
  isPublic?: boolean
}

export const profilesApi = {
  /** Profil complet du connecté (lazy-create côté backend). */
  getMe: () => http.get<MyProfile>('/profiles/me'),

  /** Profil public par pseudo (404 si inexistant ou privé). */
  getPublic: (username: string) =>
    http.get<PublicProfile>(`/profiles/username/${encodeURIComponent(username)}`),

  /** Modification partielle (champs présents uniquement). */
  updateMe: (data: UpdateProfileInput) =>
    http.put<MyProfile>('/profiles/me', data),

  /** Remplacement d'avatar (multipart, sécurisé côté backend). */
  uploadAvatar: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postForm<MyProfile>('/profiles/me/avatar', formData)
  },
}
