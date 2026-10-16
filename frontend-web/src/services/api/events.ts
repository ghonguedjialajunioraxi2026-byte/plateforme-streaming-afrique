/// Service événements temps réel — miroir de `StreamEventRestController`.
/// Publication du chat live : `POST /api/events/publish`
/// `{type:"CHAT_MESSAGE", payload:{streamId, message}}`.
/// La diffusion se fait via WebSocket (`/topic/events/chat-message`) ;
/// voir `src/realtime/useLiveChat.ts`. Aucun historique : salon live
/// transitoire (même mécanisme que l'app mobile).
import { http } from '../http'

export interface ChatPublishPayload {
  streamId: number
  authorId?: number
  authorName?: string
  message?: string
  text?: string
}

export interface EventEnvelope<T = unknown> {
  eventId: string
  type: string
  occurredAt: string
  payload: T
}

export interface ChatEventPayload {
  streamId: number
  authorId: number
  authorName: string
  /** Backend historique : `text`. Clients récents : `message`. */
  text?: string
  message?: string
  sentAt?: string
}

export const eventsApi = {
  /** Publier un message de chat (AUTH ; 401 si visiteur, 429 si spam). */
  publishChat: (payload: ChatPublishPayload) =>
    http.post<EventEnvelope<ChatEventPayload>>('/events/publish', {
      type: 'CHAT_MESSAGE',
      payload,
    }),
}
