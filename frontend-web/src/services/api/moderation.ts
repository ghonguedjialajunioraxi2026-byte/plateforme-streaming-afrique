/// Service modération (étape 17) — miroir RÉEL de `ModerationController`
/// (MODERATOR+) et `ReportController` (dépôt, tout connecté).
import { http } from '../http'

export type ReportTargetType = 'USER' | 'CHAIN' | 'STREAM' | 'VIDEO' | 'CLIP' | 'CHAT_MESSAGE'
export type ReportReason = 'SPAM' | 'HARASSMENT' | 'HATE' | 'SEXUAL' | 'VIOLENCE' | 'COPYRIGHT' | 'OTHER'
export type ReportStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED'
export type SanctionAction = 'DISMISS' | 'WARN' | 'TIMEOUT' | 'HIDE_CONTENT' | 'SUSPEND_CHAIN' | 'BAN_TEMP' | 'BAN_PERM'
export type SanctionScope = 'MESSAGE' | 'CONTENT' | 'CHAIN' | 'ACCOUNT' | 'PLATFORM'
export type SanctionState = 'ACTIVE' | 'LIFTED' | 'EXPIRED'

export interface Report {
  id: number
  reporterId: number
  targetType: ReportTargetType
  targetUserId: number | null
  targetChainId: number | null
  targetStreamId: number | null
  targetVideoId: number | null
  targetClipId: number | null
  targetMessageId: number | null
  reason: ReportReason
  description: string | null
  status: ReportStatus
  createdAt: string
}

export interface Sanction {
  id: number
  reportId: number | null
  moderatorId: number
  targetUserId: number | null
  action: SanctionAction
  scope: SanctionScope
  reason: string
  startsAt: string
  endsAt: string | null
  state: SanctionState
  createdAt: string
}

export interface ChatMessage {
  id: number
  streamId: number
  authorId: number | null
  authorName: string
  bodyDisplay: string
  system: boolean
  status: 'VISIBLE' | 'HIDDEN'
  hiddenReason: string | null
  createdAt: string
}

export interface ModerationDashboard {
  openReports: number
  activeSanctions: number
  recentOpen: {
    content: Report[]
    page: number
    size: number
    totalElements: number
    totalPages: number
  }
}

export interface UserSummary {
  id: number
  username: string
  displayName: string | null
  role: string
  status: string
  createdAt: string
  recentActions: Sanction[]
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

export const moderationApi = {
  /** Signaler (tout connecté). */
  fileReport: (targetType: ReportTargetType, targetId: number, reason: ReportReason, description?: string | null) =>
    http.post<Report>('/reports', { targetType, targetId, reason, description }),

  /** Vue d'ensemble modération. */
  dashboard: () => http.get<ModerationDashboard>('/moderator/dashboard'),

  /** File des signalements par statut. */
  reports: (status: ReportStatus = 'OPEN', page = 0, size = 20) =>
    http.get<{ content: Report[]; page: number; size: number; totalElements: number; totalPages: number }>(
      `/moderator/reports${params({ status, page, size })}`,
    ),

  /** Détail d'un signalement. */
  report: (id: number) => http.get<Report>(`/moderator/reports/${id}`),

  /** Retenir (OPEN → ACCEPTED). */
  accept: (id: number) => http.post<Report>(`/moderator/reports/${id}/accept`, {}),

  /** Classer sans suite (OPEN → REJECTED). */
  reject: (id: number) => http.post<Report>(`/moderator/reports/${id}/reject`, {}),

  /** Sanctionner (effets + notifications + audit). */
  decide: (id: number, input: { action: SanctionAction; scope: SanctionScope; targetUserId?: number | null; reason: string; endsAt?: string | null }) =>
    http.post<Sanction>(`/moderator/reports/${id}/decide`, input),

  /** Historique des sanctions. */
  actions: (state?: SanctionState, page = 0, size = 20) =>
    http.get<{ content: Sanction[]; page: number; size: number; totalElements: number; totalPages: number }>(
      `/moderator/actions${params({ state, page, size })}`,
    ),

  /** Lever une sanction active. */
  lift: (id: number) => http.post<Sanction>(`/moderator/actions/${id}/lift`, {}),

  /** Historique chat d'un live. */
  chat: (streamId: number, page = 0, size = 50) =>
    http.get<{ content: ChatMessage[]; page: number; size: number; totalElements: number; totalPages: number }>(
      `/moderator/chat${params({ streamId, page, size })}`,
    ),

  /** Masquer un message (motif, audité). */
  hideMessage: (id: number, reason: string) =>
    http.post<ChatMessage>(`/moderator/chat/${id}/hide`, { reason }),

  /** Fiche modérateur d'un compte (sans email) + sanctions. */
  userSummary: (userId: number) => http.get<UserSummary>(`/moderator/users/${userId}/summary`),
}
