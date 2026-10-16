/// Service de streaming — lives, qualité video, présence.
import { http } from '../http'
import type { Stream } from '../../types/domain'

export interface StreamDetail {
  id: number
  title: string
  description: string | null
  creatorId: number
  creatorSlug: string | null
  categoryId: number | null
  categorySlug: string | null
  categoryName: string | null
  status: string
  thumbnailUrl: string | null
  language: string | null
  visibility: string
  playbackUrl: string | null
  viewerCount: number
  peakViewers: number
  scheduledStartAt: string | null
  startedAt: string | null
  endedAt: string | null
  createdAt: string
}

export interface ScheduleStreamInput {
  title: string
  categoryId?: number | null
  scheduledStartAt?: string | null
}

export interface UpdateStreamInput {
  title: string
  categoryId?: number | null
  scheduledStartAt?: string | null
}

export interface StreamPage {
  content: StreamDetail[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ChatHistoryMessage {
  id: number
  streamId: number
  authorId: number | null
  authorName: string
  bodyDisplay: string
  system: boolean
  status: string
  hiddenReason: string | null
  createdAt: string
}

export interface ChatHistoryPage {
  content: ChatHistoryMessage[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const streamsApi = {
  getLiveStreams: () => http.get<Stream[]>('/streams/live'),
  getStream: (id: string) => http.get<Stream>(`/streams/${id}`),
  getStreamsByCategory: (slug: string) =>
    http.get<Stream[]>(`/streams/category/${slug}`),

  /** Directs EN COURS (route réelle `GET /api/streams?status=LIVE`, filtre `language` optionnel). */
  listLive: (page = 0, size = 12, language?: string) => {
    const params = new URLSearchParams({ status: 'LIVE', page: String(page), size: String(size) })
    if (language) params.set('language', language)
    return http.get<StreamPage>(`/streams?${params.toString()}`)
  },

  /** Détail réel d'un direct. */
  getDetail: (id: number) => http.get<StreamDetail>(`/streams/${id}`),

  /** Historique chat PUBLIC (messages visibles, anciens d'abord). */
  history: (streamId: number, size = 30) =>
    http.get<ChatHistoryPage>(`/streams/${streamId}/chat?size=${size}`),

  /** Directs d'une chaîne, filtrés par statut (studio : mes LIVE / programmés). */
  byCreator: (creatorId: number, status?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ creatorId: String(creatorId), page: String(page), size: String(size) })
    if (status !== undefined) params.set('status', status)
    return http.get<StreamPage>(`/streams?${params.toString()}`)
  },

  /** Terminer un direct (déclenche la VOD automatique côté serveur). */
  end: (id: number) => http.post<StreamDetail>(`/streams/${id}/end`, {}),

  /** Programmer un direct (CREATOR, propriétaire de la chaîne). */
  schedule: (creatorId: number, input: ScheduleStreamInput) =>
    http.post<StreamDetail>(`/streams?creatorId=${creatorId}`, input),

  /** Démarrer un direct programmé (passe en LIVE). */
  start: (id: number) => http.post<StreamDetail>(`/streams/${id}/start`, {}),

  /** Reprogrammer / renommer un direct (interdit si LIVE → 409). */
  update: (id: number, input: UpdateStreamInput) =>
    http.put<StreamDetail>(`/streams/${id}`, input),

  /** Retirer un direct programmé (masquage logique, interdit si LIVE → 409). */
  remove: (id: number) => http.delete<null>(`/streams/${id}`),
}

export interface IngestInfo {
  streamId: number
  rtmpUrl: string
  playbackUrl: string | null
  ingestProtocol: string
  hasKey: boolean
}

export interface StreamKeyResponse {
  streamId: number
  rtmpUrl: string
  /** Clé en clair — montrée UNE seule fois, jamais ré-exposable. */
  streamKey: string
  /** Format complet pour OBS : `<id>?key=<clé>`. */
  obsStreamKey: string
}

export const ingestApi = {
  /** Infos d'ingest du direct (propriétaire, jamais la clé). */
  info: (streamId: number) => http.get<IngestInfo>(`/streams/${streamId}/ingest`),

  /** Émettre / régénérer la stream key (propriétaire, montrée une fois). */
  issueKey: (streamId: number) => http.post<StreamKeyResponse>(`/streams/${streamId}/stream-key`, {}),
}