/// Service clips (étape 15) — miroir RÉEL de `ClipController` backend.
///
/// Feed, détail, lecture, vues, gestion, supervision.
/// Un clip a UNE source (streamId OU videoId) + un lien de partage
/// (`shareSlug`). Le découpage viewer vit sur les sources
/// (`videosApi.createClip`, direct source).
import { http } from '../http'
import type { Clip, Playback, VideoVisibility } from './videos'

export type { Clip, Playback }

export interface ClipPage {
  content: Clip[]
  page: number
  size: number
  totalElements: number
  totalPages: number
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

export interface UpdateClipInput {
  title: string
  clipUrl: string
  thumbnailUrl?: string | null
  durationSeconds: number
}

export const clipsApi = {
  /** Feed public (plus récents, PUBLIC lisibles uniquement). */
  feed: (search?: string, page = 0, size = 12) =>
    http.get<ClipPage>(`/clips/feed${params({ search, page, size })}`),

  /** Liste filtrable (chaîne, direct, vidéo, recherche). */
  list: (options?: { search?: string; creatorId?: number; streamId?: number; videoId?: number; page?: number; size?: number }) =>
    http.get<ClipPage>(
      `/clips${params({
        search: options?.search,
        creatorId: options?.creatorId,
        streamId: options?.streamId,
        videoId: options?.videoId,
        page: options?.page ?? 0,
        size: options?.size ?? 10,
      })}`,
    ),

  /** Détail (PRIVATE/supprimé = 404 si non autorisé). */
  get: (id: number) => http.get<Clip>(`/clips/${id}`),

  /** Détail par lien court de partage (/c/{shareSlug}, §40-41). */
  getByShareSlug: (shareSlug: string) => http.get<Clip>(`/clips/c/${shareSlug}`),

  /** URLs de lecture (READY exigé, démarrage à l'extrait). */
  playback: (id: number) => http.get<Playback>(`/clips/${id}/playback`),

  /** Enregistrer une vue (anti-abus serveur). */
  recordView: (id: number) => http.post<number>(`/clips/${id}/view`, {}),

  /** Mes découpages (auteur connecté). */
  mine: (page = 0, size = 10) =>
    http.get<ClipPage>(`/clips/me${params({ page, size })}`),

  /** Modifier (propriétaire de la chaîne source). */
  update: (id: number, input: UpdateClipInput) =>
    http.put<Clip>(`/clips/${id}`, input),

  /** Visibilité (propriétaire ou modération). */
  changeVisibility: (id: number, visibility: VideoVisibility) =>
    http.patch<Clip>(`/clips/${id}/visibility`, { visibility }),

  /** Masquer (logique). */
  remove: (id: number) => http.delete<null>(`/clips/${id}`),

  /** Restaurer (propriétaire ou modération, idempotent). */
  restore: (id: number) => http.post<Clip>(`/clips/${id}/restore`, {}),

  /** Supervision (ADMIN). */
  adminAll: (page = 0, size = 20) =>
    http.get<ClipPage>(`/clips/admin/all${params({ page, size })}`),
}
