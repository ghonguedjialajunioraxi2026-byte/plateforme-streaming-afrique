/// Service VOD (étape 15) — miroir RÉEL de `VideoController` backend.
///
/// Lecture publique (feed, détail, lecture, vues) + gestion créateur
/// (publication, modification, visibilité, suppression logique) +
/// supervision ADMIN. Le découpage viewer vit ici aussi
/// (`POST /api/videos/{id}/clips`).
import { http } from '../http'

export type VideoVisibility = 'PUBLIC' | 'UNLISTED' | 'PRIVATE'
export type ProcessingStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'

export interface Video {
  id: number
  title: string
  description: string | null
  creatorId: number
  categoryId: number | null
  videoUrl: string | null
  thumbnailUrl: string | null
  durationSeconds: number
  visibility: VideoVisibility
  publishedAt: string | null
  createdAt: string
  viewCount: number
  processingStatus: ProcessingStatus
  originStreamId: number | null
  deleted: boolean
}

export interface VideoPage {
  content: Video[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Playback {
  playbackUrl: string | null
  hlsUrl: string | null
  thumbnailUrl: string | null
  durationSeconds: number
  startsAtSeconds: number
}

export interface PublishVideoInput {
  title: string
  description?: string
  categoryId?: number | null
  videoUrl: string
  thumbnailUrl?: string | null
  durationSeconds: number
}

export interface CreateClipInput {
  title: string
  startTimeSeconds: number
  durationSeconds: number
  thumbnailUrl?: string | null
}

export interface Clip {
  id: number
  title: string
  creatorId: number
  streamId: number | null
  videoId: number | null
  clipUrl: string
  thumbnailUrl: string | null
  durationSeconds: number
  visibility: VideoVisibility
  createdAt: string
  shareSlug: string
  createdByUserId: number | null
  viewCount: number
  startsAtSeconds: number
  processingStatus: ProcessingStatus
  deleted: boolean
}

function params(query: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text.length > 0 ? `?${text}` : ''
}

export const videosApi = {
  /** Feed public (plus récentes, PUBLIC lisibles uniquement). */
  feed: (search?: string, category?: string, page = 0, size = 12, language?: string) =>
    http.get<VideoPage>(`/videos/feed${params({ search, category, page, size, language })}`),

  /** Liste filtrable (chaîne, catégorie, recherche, langue déclarée). */
  list: (options?: { search?: string; creatorId?: number; category?: string; language?: string; page?: number; size?: number }) =>
    http.get<VideoPage>(
      `/videos${params({
        search: options?.search,
        creatorId: options?.creatorId,
        category: options?.category,
        language: options?.language,
        page: options?.page ?? 0,
        size: options?.size ?? 10,
      })}`,
    ),

  /** Langue déclarée d'une VOD (propriétaire ou modération, null = non renseignée). */
  setLanguage: (id: number, language: string | null) =>
    http.patch<Video>(`/videos/${id}/language`, { language }),

  /** Détail (PRIVATE/supprimée = 404 si non autorisé). */
  get: (id: number) => http.get<Video>(`/videos/${id}`),

  /** URLs de lecture (READY exigé). */
  playback: (id: number) => http.get<Playback>(`/videos/${id}/playback`),

  /** Enregistrer une vue (anti-abus serveur, répond le compteur). */
  recordView: (id: number) => http.post<number>(`/videos/${id}/view`, {}),

  /** Découper un clip depuis cette VOD (tout connecté). */
  createClip: (videoId: number, input: CreateClipInput) =>
    http.post<Clip>(`/videos/${videoId}/clips`, input),

  /** Mes vidéos (chaîne du JWT, brouillons inclus). */
  mine: (page = 0, size = 10) =>
    http.get<VideoPage>(`/videos/me${params({ page, size })}`),

  /** Publier (CREATOR, propriétaire de la chaîne). */
  publish: (creatorId: number, input: PublishVideoInput) =>
    http.post<Video>(`/videos?creatorId=${creatorId}`, input),

  /** Modifier (propriétaire). */
  update: (id: number, input: PublishVideoInput) =>
    http.put<Video>(`/videos/${id}`, input),

  /** Visibilité (propriétaire ou modération). */
  changeVisibility: (id: number, visibility: VideoVisibility) =>
    http.patch<Video>(`/videos/${id}/visibility`, { visibility }),

  /** Masquer (logique — clips désactivés, fichier conservé). */
  remove: (id: number) => http.delete<null>(`/videos/${id}`),

  /** Restaurer (propriétaire ou ADMIN, idempotent). */
  restore: (id: number) => http.post<Video>(`/videos/${id}/restore`, {}),

  /** Supervision (ADMIN). */
  adminAll: (page = 0, size = 20) =>
    http.get<VideoPage>(`/videos/admin/all${params({ page, size })}`),
}
