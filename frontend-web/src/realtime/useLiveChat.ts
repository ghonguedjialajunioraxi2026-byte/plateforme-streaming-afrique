/// Chat live temps réel — STOMP minimal sur WebSocket natif + publication REST.
///
/// Même mécanisme que l'app mobile (`flick_stomp.dart` + `POST /events/publish`) :
/// - réception : `GET /ws` (STOMP 1.2 : CONNECT → SUBSCRIBE
///   `/topic/events/chat-message`, filtrage `payload.streamId`) ;
/// - envoi : `POST /api/events/publish` (AUTH, 401 visiteur, 429 anti-spam).
///
/// Salon transitoire : aucun historique serveur (le backend diffuse sans
/// persister — voir `ChatMessagePayload.java`). Visiteur non connecté :
/// lecture seule.
import { useCallback, useEffect, useRef, useState } from 'react'
import { eventsApi, type ChatEventPayload } from '../services/api/events'
import { streamsApi } from '../services/api/streams'
import { ApiError } from '../services/http'

export interface LiveChatMessage {
  id: string
  authorName: string
  text: string
  sentAt: string
}

interface UseLiveChatOptions {
  authorId?: number | null
  authorName?: string | null
}

interface UseLiveChatResult {
  messages: LiveChatMessage[]
  connected: boolean
  /** `false` = visiteur non connecté (lecture seule). */
  canSend: boolean
  sendError: string | null
  send: (text: string) => Promise<boolean>
}

const MAX_MESSAGES = 200
const TOPIC = '/topic/events/chat-message'

function wsUrl(): string {
  const api = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8080/api'
  const base = api.replace(/\/api\/?$/, '')
  return `${base.replace(/^http/, 'ws')}/ws`
}

function toMessage(payload: ChatEventPayload, eventId: string, occurredAt: string): LiveChatMessage | null {
  const streamId = Number(payload.streamId)
  if (!Number.isFinite(streamId)) return null
  const text = (payload.text ?? payload.message ?? '').trim()
  if (!text) return null
  return {
    id: eventId,
    authorName: payload.authorName || 'Anonyme',
    text,
    sentAt: payload.sentAt ?? occurredAt,
  }
}

function parseStompFrames(buffer: string): { frames: Array<{ command: string; headers: Record<string, string>; body: string }>; rest: string } {
  const frames: Array<{ command: string; headers: Record<string, string>; body: string }> = []
  let rest = buffer
  for (;;) {
    const end = rest.indexOf('\0')
    if (end === -1) break
    const raw = rest.slice(0, end)
    rest = rest.slice(end + 1)
    if (raw.trim() === '') continue
    const lines = raw.split('\n')
    const command = (lines.shift() ?? '').replace(/\r$/, '')
    const headers: Record<string, string> = {}
    let body = ''
    let i = 0
    for (; i < lines.length; i += 1) {
      const line = lines[i].replace(/\r$/, '')
      if (line === '') {
        body = lines.slice(i + 1).join('\n')
        break
      }
      const sep = line.indexOf(':')
      if (sep > 0) headers[line.slice(0, sep)] = line.slice(sep + 1)
    }
    frames.push({ command, headers, body })
  }
  return { frames, rest }
}

/**
 * Chat d'un direct. `streamId` null = pas de connexion.
 * Reconnexion exponentielle 5s → 30s (comme le mobile).
 */
export function useLiveChat(streamId: number | null, options: UseLiveChatOptions = {}): UseLiveChatResult {
  const [messages, setMessages] = useState<LiveChatMessage[]>([])
  const [connected, setConnected] = useState(false)
  const [sendError, setSendError] = useState<string | null>(null)
  const socketRef = useRef<WebSocket | null>(null)
  const bufferRef = useRef('')
  const retryRef = useRef(5000)
  const timerRef = useRef<number | null>(null)
  const closedRef = useRef(false)
  const streamRef = useRef<number | null>(streamId)
  streamRef.current = streamId
  const seenRef = useRef<Set<string>>(new Set())

  const push = useCallback((message: LiveChatMessage) => {
    if (seenRef.current.has(message.id)) return
    seenRef.current.add(message.id)
    setMessages((prev) => [...prev.slice(-(MAX_MESSAGES - 1)), message])
  }, [])

  useEffect(() => {
    if (streamId === null) return
    closedRef.current = false
    seenRef.current = new Set()
    setMessages([])

    // Historique récent (persisté serveur) puis temps réel par-dessus.
    let historyCancelled = false
    streamsApi
      .history(streamId, 30)
      .then((page) => {
        if (historyCancelled) return
        for (const item of page.content) {
          push({ id: `db-${item.id}`, authorName: item.authorName, text: item.bodyDisplay, sentAt: item.createdAt })
        }
      })
      .catch(() => undefined)

    function scheduleReconnect() {
      if (closedRef.current) return
      const delay = retryRef.current
      retryRef.current = Math.min(retryRef.current * 2, 30000)
      timerRef.current = window.setTimeout(() => connect(), delay)
    }

    function connect() {
      if (closedRef.current) return
      let socket: WebSocket
      try {
        socket = new WebSocket(wsUrl())
      } catch {
        scheduleReconnect()
        return
      }
      socketRef.current = socket

      socket.onopen = () => {
        bufferRef.current = ''
        socket.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0')
      }
      socket.onmessage = (event: MessageEvent) => {
        const chunk = typeof event.data === 'string' ? event.data : ''
        if (chunk === '\n') {
          try {
            socket.send('\n')
          } catch {
            // Heartbeat ignoré : la reconnexion prendra le relais.
          }
          return
        }
        bufferRef.current += chunk
        const { frames, rest } = parseStompFrames(bufferRef.current)
        bufferRef.current = rest
        for (const frame of frames) {
          if (frame.command === 'CONNECTED') {
            retryRef.current = 5000
            setConnected(true)
            socket.send(`SUBSCRIBE\nid:sub-chat\ndestination:${TOPIC}\nack:auto\n\n\0`)
          } else if (frame.command === 'MESSAGE') {
            try {
              const envelope = JSON.parse(frame.body) as {
                eventId?: string
                type?: string
                occurredAt?: string
                payload?: ChatEventPayload
              }
              if (envelope.type !== 'CHAT_MESSAGE' || !envelope.payload) continue
              if (Number(envelope.payload.streamId) !== streamRef.current) continue
              const message = toMessage(
                envelope.payload,
                envelope.eventId ?? `${Date.now()}-${Math.random()}`,
                envelope.occurredAt ?? new Date().toISOString(),
              )
              if (message) push(message)
            } catch {
              // Trame illisible : on ignore (jamais de crash du chat).
            }
          } else if (frame.command === 'ERROR') {
            setConnected(false)
          }
        }
      }
      socket.onerror = () => {
        // L'erreur sera suivie de onclose → reconnexion planifiée là-bas.
      }
      socket.onclose = () => {
        setConnected(false)
        if (socketRef.current === socket) socketRef.current = null
        scheduleReconnect()
      }
    }

    connect()
    return () => {
      historyCancelled = true
      closedRef.current = true
      if (timerRef.current !== null) {
        window.clearTimeout(timerRef.current)
        timerRef.current = null
      }
      try {
        const socket = socketRef.current
        socketRef.current = null
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.send('DISCONNECT\n\n\0')
        }
        socket?.close()
      } catch {
        // Fermeture best-effort.
      }
      setConnected(false)
    }
  }, [streamId, push])

  const send = useCallback(
    async (text: string): Promise<boolean> => {
      const clean = text.trim()
      if (!clean || streamId === null) return false
      if (options.authorId === null || options.authorId === undefined) {
        setSendError('Connecte-toi pour chater.')
        return false
      }
      setSendError(null)
      try {
        await eventsApi.publishChat({
          streamId,
          authorId: options.authorId,
          authorName: options.authorName ?? 'Anonyme',
          message: clean,
        })
        return true
      } catch (error) {
        if (error instanceof ApiError) {
          if (error.status === 401) setSendError('Connecte-toi pour chater.')
          else if (error.status === 429) setSendError('Ralentis — mode lent actif.')
          else setSendError(error.message)
        } else {
          setSendError('Envoi impossible — vérifie ta connexion.')
        }
        return false
      }
    },
    [streamId, options.authorId, options.authorName],
  )

  return {
    messages,
    connected,
    canSend: options.authorId !== null && options.authorId !== undefined,
    sendError,
    send,
  }
}
