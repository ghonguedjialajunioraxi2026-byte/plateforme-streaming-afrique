/// Service bêta privée (étape 24) — miroir RÉEL de `BetaController`
/// (retours, tout connecté) et `BetaAdminController` (ADMIN).
import { http } from '../http'
import type { Page } from './admin'

export type BetaInvitationStatus = 'PENDING' | 'ACCEPTED' | 'REVOKED'
export type BetaFeedbackCategory =
  | 'BUG'
  | 'UX'
  | 'SUGGESTION'
  | 'PERFORMANCE'
  | 'STREAMING'
  | 'CHAT'
  | 'PAYMENT'
  | 'OTHER'
export type BetaFeedbackStatus =
  | 'OPEN'
  | 'TRIAGED'
  | 'IN_PROGRESS'
  | 'FIXED'
  | 'VERIFIED'
  | 'CLOSED'
export type BetaPriority = 'P0' | 'P1' | 'P2' | 'P3'

export interface BetaInvitation {
  id: number
  email: string
  status: BetaInvitationStatus
  expiresAt: string
  acceptedAt: string | null
  acceptedUserId: number | null
  createdAt: string
}

export interface InvitationCreated {
  id: number
  email: string
  /** Code en clair — montré UNE fois (jamais persisté côté serveur). */
  code: string
  expiresAt: string
}

export interface BetaFeedback {
  id: number
  reporterId: number
  category: BetaFeedbackCategory
  title: string
  description: string
  route: string | null
  appVersion: string | null
  device: string | null
  status: BetaFeedbackStatus
  priority: BetaPriority | null
  createdAt: string
}

export interface BetaStats {
  invitations: number
  pending: number
  accepted: number
  feedback: number
  feedbackOpen: number
}

/** Version applicative affichée dans les retours (identifiant de bêta). */
export const BETA_APP_VERSION = 'beta-0.2.0'

export const betaApi = {
  /** Signaler un retour (201, statut OPEN forcé serveur). */
  submitFeedback: (input: {
    category: BetaFeedbackCategory
    title: string
    description: string
    route?: string
    appVersion?: string
    device?: string
  }) => http.post<BetaFeedback>('/beta/feedback', input),

  /** Mes retours (paginés, soi uniquement côté serveur). */
  myFeedback: (page = 0, size = 20) =>
    http.get<Page<BetaFeedback>>(`/beta/feedback/mine?page=${page}&size=${size}`),

  /** Compteurs réels (ADMIN). */
  stats: () => http.get<BetaStats>('/admin/beta/stats'),

  /** Inviter (ADMIN) — le code n'apparaît que dans cette réponse. */
  invite: (email: string, note?: string) =>
    http.post<InvitationCreated>('/admin/beta/invitations', { email, note }),

  /** Invitations (ADMIN, jamais de code en clair). */
  invitations: (status?: BetaInvitationStatus, page = 0, size = 20) =>
    http.get<Page<BetaInvitation>>(
      `/admin/beta/invitations?page=${page}&size=${size}${status ? `&status=${status}` : ''}`,
    ),

  /** Révoquer (ADMIN, PENDING uniquement). */
  revoke: (id: number) =>
    http.post<BetaInvitation>(`/admin/beta/invitations/${id}/revoke`, {}),

  /** Retours (ADMIN, filtre statut). */
  feedback: (status?: BetaFeedbackStatus, page = 0, size = 20) =>
    http.get<Page<BetaFeedback>>(
      `/admin/beta/feedback?page=${page}&size=${size}${status ? `&status=${status}` : ''}`,
    ),

  /** Trier (ADMIN) : statut + priorité P0..P3. */
  triage: (id: number, status: BetaFeedbackStatus, priority: BetaPriority | null) =>
    http.put<BetaFeedback>(`/admin/beta/feedback/${id}/triage`, { status, priority }),
}
