/// Service notifications — miroir RÉEL de `NotificationController` backend.
///
/// Lectures AUTHENTIFIÉES (étape 22 / SEC-001 : destinataire ou ADMIN —
/// le serveur vérifie l'acteur depuis le JWT, jamais le `recipientId` seul).
/// Marquages « lu » = destinataire uniquement (403 sinon).
import { http } from '../http'

export interface NotificationItem {
  id: number
  type: string
  title: string
  message: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
}

export interface NotificationPage {
  content: NotificationItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const notificationsApi = {
  /** Notifications d'un compte (paginé, plus récentes d'abord côté serveur). */
  list: (recipientId: number, page = 0, size = 20) =>
    http.get<NotificationPage>(`/notifications?recipientId=${recipientId}&page=${page}&size=${size}`),

  /** Nombre de non-lues (cloche de la navbar). */
  unreadCount: (recipientId: number) =>
    http.get<number>(`/notifications/unread-count?recipientId=${recipientId}`),

  /** Marquer UNE notification comme lue (destinataire uniquement). */
  markAsRead: (id: number) => http.patch<NotificationItem>(`/notifications/${id}/read`, {}),

  /** Tout marquer comme lu (soi-même). */
  markAllAsRead: (recipientId: number) =>
    http.post<number>(`/notifications/${recipientId}/read-all`, {}),
}
