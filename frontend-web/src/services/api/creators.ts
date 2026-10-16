/// Service chaînes (étape 09) — miroir RÉEL de `CreatorController` backend.
///
/// REMPLACE l'ancien contrat (routes `/creators/:x/channel|stats|follow`
/// inexistantes côté backend, jamais appelées par aucun composant) :
/// lecture par slug/id, ouverture (auto-promotion CREATOR), modification
/// owner, fermeture logique, visuels par upload. Les suivis vivent dans
/// `services/api/follows.ts` (étape 12) — pas ici.
import { http } from '../http'
import type { ChannelDetail } from '../../types/domain'
import type { Clip, Video, VideoPage } from './videos'
import type { ClipPage } from './clips'

export interface CreateChannelInput {
  displayName: string
  bio?: string
  avatarUrl?: string
  bannerUrl?: string
  primaryCategoryId?: number | null
}

export interface UpdateChannelInput {
  displayName: string
  bio?: string | null
  avatarUrl?: string | null
  bannerUrl?: string | null
  primaryCategoryId?: number | null
}

export interface ChannelPage {
  content: ChannelDetail[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const creatorsApi = {
  /** Page publique des chaînes (recherche + pagination backend). */
  list: (search?: string, page = 0, size = 12) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search !== undefined && search.trim().length > 0) {
      params.set('search', search.trim())
    }
    return http.get<ChannelPage>(`/creators?${params.toString()}`)
  },

  /** Fiche publique par slug (404 si fermée/inexistante). */
  getBySlug: (slug: string) =>
    http.get<ChannelDetail>(`/creators/slug/${encodeURIComponent(slug)}`),

  /** Fiche par id (public). */
  getById: (id: number) => http.get<ChannelDetail>(`/creators/${id}`),

  /** Ma chaîne (JWT — jamais de userId frontend, étape 09 §5/§11). */
  getMyChannel: () => http.get<ChannelDetail>('/creators/me'),

  /** Chaîne d'un compte (404 si aucune — normal pour un non-créateur). */
  getByUserId: (userId: number) =>
    http.get<ChannelDetail>(`/creators/user/${userId}`),

  /** Ouvrir sa chaîne (PROMEUT en CREATOR ; une seule par compte). */
  create: (userId: number, data: CreateChannelInput) =>
    http.post<ChannelDetail>(`/creators?userId=${userId}`, data),

  /** Modifier sa chaîne (propriétaire ou ADMIN côté backend). */
  update: (id: number, data: UpdateChannelInput) =>
    http.put<ChannelDetail>(`/creators/${id}`, data),

  /** Fermer sa chaîne (logique : invisible en public, contenus intacts). */
  remove: (id: number) => http.delete<null>(`/creators/${id}`),

  /** Remplacer l'avatar (multipart, sécurisé côté backend). */
  uploadAvatar: (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postForm<ChannelDetail>(`/creators/${id}/avatar`, formData)
  },

  /** Remplacer la bannière (recommandé 16:6 côté affichage). */
  uploadBanner: (id: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return http.postForm<ChannelDetail>(`/creators/${id}/banner`, formData)
  },

  /** VOD publiques d'une chaîne (équivalent spec `/api/channels/{id}/videos`, §22/§43). */
  getVideos: (id: number, page = 0, size = 10) =>
    http.get<VideoPage>(`/creators/${id}/videos?page=${page}&size=${size}`),

  /** Clips publics d'une chaîne (§44). */
  getClips: (id: number, page = 0, size = 10) =>
    http.get<ClipPage>(`/creators/${id}/clips?page=${page}&size=${size}`),
}

export type { Clip, Video }
