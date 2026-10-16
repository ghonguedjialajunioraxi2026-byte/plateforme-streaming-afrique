import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { ChatMessage } from '../../types/domain'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'

interface ChatPanelProps {
  messages: ChatMessage[]
  /** Simule l'état de la connexion WebSocket */
  isConnected?: boolean
  /** Utilisateur local (ses messages sont mis en évidence) */
  currentUsername?: string
  className?: string
  /** Mode intégré : sans bordure ni en-tête (le parent fournit le cadre) */
  bare?: boolean
  /** Envoi réel (chat live). Absent = saisie décorative (maquette). */
  onSend?: (text: string) => Promise<boolean> | boolean
  /** Erreur du dernier envoi (affichée sous la saisie). */
  sendError?: string | null
  /** Envoi en cours (désactive la saisie). */
  sending?: boolean
}

/** Rôles reconnus (backend : `badges[]`) → pastille + label. */
const ROLE_BADGES: Record<string, { label: string; className: string }> = {
  broadcaster: { label: 'Diffuseur', className: 'bg-primary-500 text-text-inverse' },
  moderator: { label: 'Modo', className: 'bg-success/20 text-accent-300' },
  vip: { label: 'VIP', className: 'bg-warning/20 text-warning' },
  subscriber: { label: 'Abonné', className: 'bg-danger/20 text-danger' },
  founder: { label: 'Fondateur', className: 'bg-danger/20 text-danger' },
  staff: { label: 'Staff', className: 'bg-text-secondary/20 text-text-secondary' },
}

/** Palette de pseudos lisible sur fond sombre (le vert = système/live). */
const NAME_COLORS = [
  'text-danger',
  'text-warning',
  'text-accent-300',
  'text-[#5aa9ff]',
  'text-[#c07bff]',
  'text-[#ff7ad9]',
  'text-[#ffb35a]',
  'text-[#7de3e0]',
] as const

function nameColor(username: string): string {
  let hash = 0
  for (let i = 0; i < username.length; i += 1) {
    hash = (hash * 31 + username.charCodeAt(i)) >>> 0
  }
  return NAME_COLORS[hash % NAME_COLORS.length]
}

/**
 * Chat temps réel façon Twitch : pseudos colorés, badges de rôle,
 * auto-scroll intelligent (bouton « Reprendre » quand on remonte),
 * apparition en douceur des nouveaux messages (rise 300ms).
 */
export function ChatPanel({
  messages,
  isConnected = false,
  currentUsername,
  className,
  bare = false,
  onSend,
  sendError,
  sending = false,
}: ChatPanelProps) {
  const [draft, setDraft] = useState('')
  const [stuckToBottom, setStuckToBottom] = useState(true)
  const listRef = useRef<HTMLDivElement>(null)

  function scrollToBottom(smooth = true) {
    const list = listRef.current
    if (!list) return
    list.scrollTo({ top: list.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }

  // Nouveaux messages : on suit le direct sauf si l'utilisateur a remonté.
  useEffect(() => {
    if (stuckToBottom) scrollToBottom(false)
  }, [messages.length, stuckToBottom])

  function handleScroll() {
    const list = listRef.current
    if (!list) return
    const distance = list.scrollHeight - list.scrollTop - list.clientHeight
    setStuckToBottom(distance < 48)
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || !isConnected || sending) return
    if (onSend) {
      const ok = await onSend(text)
      if (ok) setDraft('')
    } else {
      // Aucun salon branché (maquette) : on vide la saisie sans envoi.
      setDraft('')
    }
    setStuckToBottom(true)
  }

  return (
    <div
      className={cn(
        'flex h-full flex-col overflow-hidden',
        !bare && 'rounded-xl border border-border-default bg-bg-surface',
        className,
      )}
    >
      {/* En-tête */}
      {!bare && (
        <div className="flex items-center justify-between border-b border-border-default px-4 py-3">
          <h2 className="font-display text-sm font-bold tracking-wide text-text-primary uppercase">
            Chat du stream
          </h2>
          <span className="flex items-center gap-1.5 text-xs font-medium text-text-muted">
            <span
              className={cn(
                'h-2 w-2 rounded-full',
                isConnected ? 'animate-pulse-live bg-live' : 'bg-text-muted',
              )}
            />
            {isConnected ? 'Connecté' : 'Déconnecté'}
          </span>
        </div>
      )}

      {/* Messages */}
      <div className="relative min-h-0 flex-1">
        <div
          ref={listRef}
          onScroll={handleScroll}
          className={cn('h-full space-y-0.5 overflow-y-auto', bare ? 'p-3' : 'p-4')}
          aria-live="polite"
          aria-label="Messages du chat"
        >
          {messages.length === 0 && (
            <div className="flex flex-col items-center gap-2 px-4 pt-10 text-center">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-bg-elevated text-text-muted">
                <Icon name="chat" className="h-6 w-6" />
              </span>
              <p className="text-sm font-semibold text-text-primary">
                Bienvenue dans le chat !
              </p>
              <p className="text-sm text-text-muted">
                Aucun message pour le moment. Sois le premier à saluer ! 👋
              </p>
            </div>
          )}
          {messages.map((message) => {
            const isOwn = message.username === currentUsername
            const roles = (message.badges ?? []).filter((badge) => ROLE_BADGES[badge] !== undefined)
            return (
              <p
                key={message.id}
                className={cn(
                  'animate-rise rounded px-1 py-0.5 text-sm leading-relaxed break-words transition-colors duration-200 hover:bg-bg-elevated',
                  isOwn && 'bg-primary-500/5',
                )}
              >
                {roles.map((role) => (
                  <span
                    key={role}
                    title={ROLE_BADGES[role].label}
                    className={cn(
                      'mr-1 inline-block rounded px-1 py-px align-middle text-[10px] font-bold tracking-wide uppercase',
                      ROLE_BADGES[role].className,
                    )}
                  >
                    {ROLE_BADGES[role].label}
                  </span>
                ))}
                <span className={cn('font-semibold', isOwn ? 'text-primary-300' : nameColor(message.username))}>
                  {message.username}
                </span>
                <span className="text-text-muted">: </span>
                <span className="text-text-primary">{message.content}</span>
              </p>
            )
          })}
        </div>

        {/* Reprendre le direct */}
        {!stuckToBottom && messages.length > 0 && (
          <button
            type="button"
            onClick={() => scrollToBottom()}
            className="animate-fade-in absolute inset-x-4 bottom-3 rounded-lg bg-primary-500 py-2 text-sm font-semibold text-text-inverse shadow-lg transition-colors duration-200 hover:bg-primary-400"
          >
            Reprendre le direct ↓
          </button>
        )}
      </div>

      {/* Saisie — maquette mobile : champ arrondi + emoji + envoi vert */}
      <form onSubmit={(event) => void handleSubmit(event)} className="border-t border-border-default p-3">
        <div className="flex items-center gap-2">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={isConnected ? 'Écrire un message…' : 'Connecte-toi pour chater'}
            disabled={!isConnected || sending}
            aria-label="Envoyer un message dans le chat"
            className="h-11 min-w-0 flex-1 rounded-full border border-border-default bg-bg-elevated px-4 text-sm text-text-primary placeholder:text-text-muted transition-colors duration-200 focus:border-primary-500 focus:outline-none disabled:opacity-50"
          />
          <button
            type="button"
            onClick={() => setDraft((current) => `${current}🔥`)}
            disabled={!isConnected || sending}
            aria-label="Ajouter un emoji"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-lg text-text-secondary transition-colors duration-200 active:bg-bg-hover active:text-text-primary disabled:opacity-50"
          >
            <span aria-hidden="true">😀</span>
          </button>
          <button
            type="submit"
            disabled={!isConnected || !draft.trim() || sending}
            aria-label="Envoyer le message"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-primary-500 text-text-inverse transition-colors duration-200 active:bg-primary-400 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Icon name="send" className="h-5 w-5" />
          </button>
        </div>
        {sendError && (
          <p role="alert" className="mt-2 px-2 text-xs text-danger">
            {sendError}
          </p>
        )}
        {!isConnected && (
          <p className="mt-2 px-2 text-xs text-text-muted">
            Le chat en direct est en lecture seule sans compte.{' '}
            <a href={`/connexion?next=${encodeURIComponent(window.location.pathname)}`} className="font-semibold text-primary-400 hover:underline">
              Connecte-toi pour participer
            </a>
            .
          </p>
        )}
      </form>
    </div>
  )
}
