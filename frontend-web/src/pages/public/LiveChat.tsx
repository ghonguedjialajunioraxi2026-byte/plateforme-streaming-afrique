import { Link, useParams } from 'react-router'
import { useEffect, useState } from 'react'
import { ChatPanel } from '../../components/streaming'
import { EmptyState } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { usePageMeta } from '../../hooks/usePageMeta'
import { useLiveChat } from '../../realtime/useLiveChat'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import type { ChatMessage } from '../../types/domain'

/**
 * Chat plein écran mobile : salon RÉEL du direct (`GET /streams/:id` +
 * WebSocket STOMP). Le desktop renvoie vers la page live
 * (chat déjà en colonne fixe).
 */
export default function LiveChat() {
  const { id = '' } = useParams<{ id: string }>()
  const streamId = Number(id)
  const validId = Number.isInteger(streamId) && streamId > 0
  const { isAuthenticated, user } = useAuth()

  const [detail, setDetail] = useState<StreamDetail | null>(null)

  const chat = useLiveChat(validId ? streamId : null, {
    authorId: user?.id ?? null,
    authorName: user?.displayName ?? user?.username ?? null,
  })

  useEffect(() => {
    if (!validId) return
    let cancelled = false
    streamsApi
      .getDetail(streamId)
      .then((data) => {
        if (!cancelled) setDetail(data)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [streamId, validId])

  const channelName = detail?.creatorSlug ?? (detail ? `Chaîne ${detail.creatorId}` : `Chaîne ${id}`)
  usePageMeta(`Chat — ${channelName}`, `Chat en direct de ${channelName} sur FLICK.`)

  const messages: ChatMessage[] = chat.messages.map((m) => ({
    id: m.id,
    streamId: String(streamId),
    userId: '',
    username: m.authorName,
    content: m.text,
    createdAt: m.sentAt,
  }))

  return (
    <>
      {/* ============ MOBILE ============ */}
      <div className="mx-auto w-full max-w-xl space-y-4 md:hidden">
        {!validId || !isAuthenticated ? (
          <EmptyState
            icon="chat"
            title={!validId ? 'Chat introuvable' : 'Connecte-toi pour chater'}
            description={
              !validId
                ? "Cet identifiant de live est invalide."
                : "Le chat en direct est en lecture seule sans compte. Connecte-toi pour participer."
            }
            action={
              !validId ? undefined : (
                <Link
                  to={`/connexion?next=${encodeURIComponent(`/live/${id}/chat`)}`}
                  className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse hover:bg-primary-400"
                >
                  Se connecter
                </Link>
              )
            }
          />
        ) : (
          <section aria-label={`Chat en direct de ${channelName}`}>
            <ChatPanel
              messages={messages}
              isConnected={chat.canSend}
              currentUsername={user?.username}
              onSend={chat.send}
              sendError={chat.sendError}
              className="h-[62vh]"
            />
            {!chat.connected && (
              <p className="mt-1 px-1 text-xs text-text-muted">Connexion au chat en cours…</p>
            )}
          </section>
        )}

        <Link
          to={`/live/${id}`}
          className="flex min-h-11 items-center justify-center gap-2 rounded-xl border border-border-default bg-bg-surface text-sm font-semibold text-text-secondary transition-colors active:bg-bg-elevated active:text-text-primary"
        >
          <Icon name="arrow-left" className="h-4 w-4" />
          Retour au live
        </Link>
      </div>

      {/* ============ DESKTOP — renvoi vers le live ============ */}
      <div className="mx-auto hidden w-full max-w-3xl md:block">
        <EmptyState
          icon="chat"
          title="Le chat vit sur la page du live"
          description="Sur grand écran, le chat est affiché en colonne fixe à côté du player."
          action={
            <Link
              to={`/live/${id}`}
              className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
            >
              Retour au live
            </Link>
          }
        />
      </div>
    </>
  )
}
