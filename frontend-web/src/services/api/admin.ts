/// Service administration (étape 17) — miroir RÉEL de `AdminController`.
/// ADMIN uniquement (backend). Pagination backend partout.
import { http } from '../http'
import type { ChannelDetail } from '../../types/domain'

export type UserRole = 'USER' | 'CREATOR' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'
export type ChannelStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED'

export interface AdminUser {
  id: number
  username: string
  email: string
  displayName: string | null
  avatarUrl: string | null
  role: UserRole
  status: UserStatus
  lastLoginAt: string | null
  emailVerified: boolean
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AdminDashboard {
  usersTotal: number
  usersActive: number
  usersSuspended: number
  creatorsTotal: number
  liveStreams: number
  videosTotal: number
  clipsTotal: number
  followersTotal: number
  subscriptionsActive: number
  paymentsByStatus: Record<string, number>
  openReports: number
  auditEntries: number
}

export interface AuditLog {
  id: number
  actorId: number
  actorUsername: string
  action: string
  targetType: string
  targetId: number | null
  beforeJson: string | null
  afterJson: string | null
  reason: string | null
  createdAt: string
}

export interface AppConfig {
  key: string
  value: string
  version: number
  effectiveFrom: string
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

export const adminApi = {
  /** Compteurs plateforme. */
  dashboard: () => http.get<AdminDashboard>('/admin/dashboard'),

  /** Recherche comptes (texte, rôle, statut). */
  users: (options?: { search?: string; role?: UserRole; status?: UserStatus; page?: number; size?: number }) =>
    http.get<Page<AdminUser>>(
      `/admin/users${params({
        search: options?.search,
        role: options?.role,
        status: options?.status,
        page: options?.page ?? 0,
        size: options?.size ?? 20,
      })}`,
    ),

  /** Statut d'un compte (motif obligatoire, audité). */
  setUserStatus: (id: number, status: UserStatus, reason: string) =>
    http.patch<AdminUser>(`/admin/users/${id}/status`, { status, reason }),

  /** Rôle d'un compte (route existante, ADMIN). */
  setUserRole: (id: number, role: UserRole) =>
    http.put<AdminUser>(`/users/${id}/role`, { role }),

  /** Recherche chaînes (texte, statut, CLOSED incluses). */
  creators: (options?: { search?: string; status?: ChannelStatus; page?: number; size?: number }) =>
    http.get<Page<ChannelDetail>>(
      `/admin/creators${params({
        search: options?.search,
        status: options?.status,
        page: options?.page ?? 0,
        size: options?.size ?? 20,
      })}`,
    ),

  /** Suspendre une chaîne (motif, audité). */
  suspendCreator: (id: number, reason: string) =>
    http.post<ChannelDetail>(`/admin/creators/${id}/suspend`, { reason }),

  /** Réactiver une chaîne suspendue (motif, audité). */
  reactivateCreator: (id: number, reason: string) =>
    http.post<ChannelDetail>(`/admin/creators/${id}/reactivate`, { reason }),

  /** Arrêter un LIVE (VOD auto + audit). */
  stopStream: (id: number) => http.post<null>(`/admin/streams/${id}/stop`, {}),

  /** Journal d'audit (filtres action/acteur). */
  audit: (options?: { action?: string; actorId?: number; page?: number; size?: number }) =>
    http.get<Page<AuditLog>>(
      `/admin/audit${params({
        action: options?.action,
        actorId: options?.actorId,
        page: options?.page ?? 0,
        size: options?.size ?? 20,
      })}`,
    ),

  /** Paramètres courants. */
  config: () => http.get<Page<AppConfig>>('/admin/config?page=0&size=50'),

  /** Nouvelle version d'un paramètre (audité). */
  setConfig: (key: string, value: string) =>
    http.put<AppConfig>('/admin/config', { key, value }),

  /** Notification ciblée (retourne le nombre de notifiés). */
  notify: (userIds: number[], type: string, title: string, message: string, linkUrl?: string | null) =>
    http.post<number>('/admin/notifications', { userIds, type, title, message, linkUrl }),
}
