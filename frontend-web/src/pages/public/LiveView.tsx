import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { SealCheck, MapPin, Star, Gift, ShareNetwork, Heart, Eye } from '@phosphor-icons/react'
import { FollowButton, ShareModal, SubscribeModal, DonateModal, ChatPanel, VideoPlayer } from '../../components/streaming'
import { FlickAvatar } from '../../components/flick/FlickAvatar'
import { FlickLiveBadge } from '../../components/flick/FlickLiveBadge'
import { EmptyState } from '../../components/ui'
import { useAuth } from '../../features/auth/useAuth'
import { usePageMeta } from '../../hooks/usePageMeta'
import { useLiveChat } from '../../realtime/useLiveChat'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import { creatorsApi } from '../../services/api/creators'
import type { ChatMessage } from '../../types/domain'
import { cn } from '../../utils/cn'

type StreamModal = 'share' | 'subscribe' | 'donate' | null

/**
 * Route `/live/:id` — données RÉELLES uniquement :
 * détail (`GET /streams/:id`), lecture HLS (`playbackUrl`, hls.js),
 * chat temps réel (WebSocket STOMP + `POST /events/publish`),
 * compteur viewers (rechargé toutes les 20s).
 * Visiteur non connecté : lecture + chat en lecture seule.
 */
export default function LiveView() {
  const { id = '' } = useParams<{ id: string }>()
  const streamId = Number(id)
  const validId = Number.isInteger(streamId) && streamId > 0
  const { isAuthenticated, user } = useAuth()

  const [detail, setDetail] = useState<StreamDetail | null>(null)
  const [related, setRelated] = useState<StreamDetail[]>([])
  const [creatorName, setCreatorName] = useState<string>('')
  const [channelId, setChannelId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [modal, setModal] = useState<StreamModal>(null)
  const [liked, setLiked] = useState(false)

  const chat = useLiveChat(validId ? streamId : null, {
    authorId: user?.id ?? null,
    authorName: user?.displayName ?? user?.username ?? null,
  })

  const loadDetail = useCallback(
    async (silent = false) => {
      if (!validId) return
      if (!silent) setLoading(true)
      try {
        const data = await streamsApi.getDetail(streamId)
        setDetail(data)
        setNotFound(false)
        setCreatorName(data.creatorSlug ?? `Chaîne ${data.creatorId}`)
      } catch {
        if (!silent) setNotFound(true)
      } finally {
        if (!silent) setLoading(false)
      }
    },
    [streamId, validId],
  )

  useEffect(() => {
    setDetail(null)
    setNotFound(false)
    void loadDetail()
  }, [loadDetail])

  // Viewers + statut : rechargement périodique (le backend agrège Redis).
  useEffect(() => {
    if (!validId || notFound) return
    const timer = window.setInterval(() => void loadDetail(true), 20000)
    return () => window.clearInterval(timer)
  }, [validId, notFound, loadDetail])

  // Chaîne réelle (follow) + lives liés.
  useEffect(() => {
    if (!detail) return
    let cancelled = false
    creatorsApi
      .getById(detail.creatorId)
      .then((channel) => {
        if (cancelled) return
        setCreatorName(channel.displayName)
        setChannelId(channel.id)
      })
      .catch(() => {
        if (!cancelled) setChannelId(null)
      })
    streamsApi
      .byCreator(detail.creatorId, 'LIVE', 0, 5)
      .then((page) => {
        if (!cancelled) setRelated(page.content.filter((s) => s.id !== detail.id).slice(0, 4))
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [detail])

  usePageMeta(
    detail?.title ?? 'Live',
    detail ? `Regarder ${detail.title} en direct sur FLICK.` : 'Regarder un live sur FLICK.',
  )

  const chatMessages: ChatMessage[] = chat.messages.map((m) => ({
    id: m.id,
    streamId: String(streamId),
    userId: '',
    username: m.authorName,
    content: m.text,
    createdAt: m.sentAt,
  }))

  const actionClass =
    'flex h-10 items-center gap-1.5 rounded-lg border border-border-default bg-bg-elevated px-3 text-sm font-semibold text-text-secondary transition-colors hover:border-border-hover hover:text-text-primary'

  if (!validId) {
    return (
      <div className="mx-auto w-full max-w-3xl">
        <EmptyState icon="video" title="Live introuvable" description="Cet identifiant de live est invalide." />
      </div>
    )
  }

  if (loading) {
    return (
      <div className="mx-auto w-full max-w-[1600px]" aria-busy="true" aria-label="Chargement du live">
        <div className="aspect-video w-full animate-pulse rounded-2xl bg-bg-elevated" />
        <div className="mt-4 h-24 animate-pulse rounded-2xl bg-bg-elevated" />
      </div>
    )
  }

  if (notFound || !detail) {
    return (
      <div className="mx-auto w-full max-w-3xl">
        <EmptyState
          icon="video"
          title="Live introuvable"
          description="Ce direct n'existe pas ou n'est plus disponible."
          action={
            <Link
              to="/explorer"
              className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse hover:bg-primary-400"
            >
              Explorer les lives
            </Link>
          }
        />
      </div>
    )
  }

  const isLive = detail.status === 'LIVE'
  const channelName = creatorName || detail.creatorSlug || `Chaîne ${detail.creatorId}`

  const streamInfo = (
    <div className="rounded-2xl border border-border-default bg-bg-surface p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        {isLive ? (
          <FlickLiveBadge />
        ) : (
          <span className="rounded bg-bg-elevated px-1.5 py-0.5 text-[10px] font-bold tracking-wide text-text-muted uppercase">
            Terminé
          </span>
        )}
        {detail.categoryName && (
          <span className="rounded-full bg-bg-elevated px-2.5 py-0.5 text-xs font-medium text-text-secondary">
            {detail.categoryName}
          </span>
        )}
        {detail.language && (
          <span className="rounded-full bg-bg-elevated px-2.5 py-0.5 text-xs font-medium text-text-muted">
            {detail.language}
          </span>
        )}
      </div>
      <h1 className="mt-2.5 font-display text-xl font-black tracking-tight text-text-primary">{detail.title}</h1>
      {detail.description && <p className="mt-2 text-sm text-text-secondary">{detail.description}</p>}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Link to={detail.creatorSlug ? `/channel/${detail.creatorSlug}` : '#'} className="group flex min-w-0 flex-1 items-center gap-3">
          <FlickAvatar name={channelName} hue={Math.abs(detail.creatorId * 37) % 360} size="md" />
          <span className="min-w-0">
            <span className="flex items-center gap-1 truncate font-bold text-text-primary group-hover:text-primary-300">
              {channelName}
              <SealCheck size={16} weight="fill" className="text-primary-400" aria-label="Chaîne" />
            </span>
            <span className="mt-0.5 flex items-center gap-2 text-xs text-text-muted">
              <span className="flex items-center gap-1">
                <Eye size={13} />
                {detail.viewerCount} spectateurs
              </span>
              {detail.startedAt && <span aria-hidden="true">·</span>}
              {detail.startedAt && <span>{new Date(detail.startedAt).toLocaleString()}</span>}
            </span>
          </span>
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <FollowButton channelId={channelId} channelName={channelName} />
          <button type="button" onClick={() => setModal('subscribe')} className={actionClass}>
            <Star size={16} className="text-amber-400" weight="fill" />
            S'abonner
          </button>
          <button type="button" onClick={() => setModal('donate')} className={actionClass}>
            <Gift size={16} />
            Cadeau
          </button>
          <button type="button" onClick={() => setModal('share')} aria-label="Partager ce live" className={actionClass}>
            <ShareNetwork size={16} />
            <span className="hidden sm:inline">Partager</span>
          </button>
        </div>
      </div>

      <div className="mt-3 flex items-center gap-2 xl:hidden">
        <button
          type="button"
          onClick={() => setLiked((v) => !v)}
          aria-pressed={liked}
          className={cn(
            'flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border text-sm font-semibold transition-colors active:scale-[0.98]',
            liked
              ? 'border-danger/60 bg-danger/10 text-danger'
              : 'border-border-default bg-bg-elevated text-text-secondary',
          )}
        >
          <Heart size={18} weight={liked ? 'fill' : 'regular'} />
          {liked ? 'Aimé' : "J'aime"}
        </button>
        <button
          type="button"
          onClick={() => setModal('subscribe')}
          className="flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-500 text-sm font-bold text-text-inverse active:bg-primary-400"
        >
          <Star size={18} weight="fill" />
          S'abonner
        </button>
      </div>
    </div>
  )

  return (
    <div className="mx-auto w-full max-w-[1600px]">
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-4">
          <VideoPlayer hlsUrl={isLive ? detail.playbackUrl : null} poster={detail.thumbnailUrl ?? undefined} />
          {!isLive && (
            <p className="rounded-xl border border-border-default bg-bg-surface px-4 py-3 text-sm text-text-secondary">
              Ce direct est terminé. La VOD sera disponible sur la chaîne dès son traitement.
            </p>
          )}
          {streamInfo}

          <div className="xl:hidden">
            <div className="mb-2 flex items-center justify-between">
              <h2 className="font-display text-[17px] font-bold tracking-tight text-text-primary">Chat en direct</h2>
              <Link to={`/live/${detail.id}/chat`} className="flex min-h-11 items-center rounded-lg px-2 text-[13px] font-semibold text-primary-400">
                Plein écran →
              </Link>
            </div>
            <ChatPanel
              messages={chatMessages}
              isConnected={chat.canSend}
              currentUsername={user?.username}
              onSend={chat.send}
              sendError={chat.sendError}
              className="h-[55vh]"
            />
            {!chat.connected && isLive && (
              <p className="mt-1 px-1 text-xs text-text-muted">Connexion au chat en cours…</p>
            )}
          </div>

          {related.length > 0 && (
            <section aria-label="À regarder ensuite" className="pt-2">
              <h2 className="mb-4 font-display text-lg font-bold tracking-tight text-text-primary">À regarder ensuite</h2>
              <div className="grid grid-cols-1 gap-x-4 gap-y-6 sm:grid-cols-2">
                {related.map((candidate) => (
                  <Link
                    key={candidate.id}
                    to={`/live/${candidate.id}`}
                    className="group rounded-xl border border-border-default bg-bg-surface p-4 transition-all hover:border-primary-500/40"
                  >
                    <p className="line-clamp-1 font-bold text-text-primary group-hover:text-primary-300">
                      {candidate.title}
                    </p>
                    <p className="mt-1 flex items-center gap-1 text-xs text-text-muted">
                      <Eye size={13} /> {candidate.viewerCount} spectateurs
                      {candidate.categoryName ? ` · ${candidate.categoryName}` : ''}
                    </p>
                  </Link>
                ))}
              </div>
            </section>
          )}
        </div>

        {/* Chat desktop fixe 320px */}
        <aside aria-label="Chat du stream en direct" className="hidden min-h-0 xl:block">
          <div className="sticky top-20 h-[calc(100vh-6rem)]">
            <ChatPanel
              messages={chatMessages}
              isConnected={chat.canSend}
              currentUsername={user?.username}
              onSend={chat.send}
              sendError={chat.sendError}
              className="h-full"
            />
            <p className="mt-2 flex items-center gap-1.5 px-1 text-xs text-text-muted">
              <Eye size={14} />
              {detail.viewerCount} spectateurs · {chat.connected ? 'chat connecté' : 'connexion chat…'}
            </p>
          </div>
        </aside>
      </div>

      <ShareModal open={modal === 'share'} onClose={() => setModal(null)} title={detail.title} channelName={channelName} />
      <SubscribeModal open={modal === 'subscribe'} onClose={() => setModal(null)} channelName={channelName} />
      <DonateModal open={modal === 'donate'} onClose={() => setModal(null)} channelName={channelName} />
      {!isAuthenticated && (
        <p className="mt-4 rounded-xl border border-border-default bg-bg-surface px-4 py-3 text-sm text-text-secondary">
          <MapPin size={14} className="mr-1 inline" />
          Tu regardes en visiteur. <Link to={`/connexion?next=${encodeURIComponent(`/live/${detail.id}`)}`} className="font-semibold text-primary-400 hover:underline">Connecte-toi</Link> pour chater, suivre et soutenir ce créateur.
        </p>
      )}
    </div>
  )
}
