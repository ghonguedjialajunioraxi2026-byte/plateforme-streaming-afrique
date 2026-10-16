import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { creatorsApi } from '../../services/api/creators'
import { videosApi, type Video } from '../../services/api/videos'
import { clipsApi } from '../../services/api/clips'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import type { Clip } from '../../services/api/videos'
import type { ChannelDetail } from '../../types/domain'
import { mediaUrl } from '../../utils/media'
import { formatCount } from '../../utils/format'
import { Avatar, Badge, Card, EmptyState, Skeleton, Tabs } from '../../components/ui'
import { FollowButton, LiveBadge, SubscribeModal, ViewerCount } from '../../components/streaming'
import { ClipCard, VideoCard } from '../../components/video'

type Status = 'loading' | 'ready' | 'not-found' | 'error'

const TABS = [
  { key: 'videos', label: 'Vidéos' },
  { key: 'lives', label: 'Lives' },
  { key: 'clips', label: 'Clips' },
  { key: 'about', label: 'À propos' },
]

/**
 * Page publique de chaîne (étape 09, §21).
 *
 * Avatar, bannière, nom, catégorie, description, pays/langue (publics),
 * followers, liens — SANS lecteur live (emplacement réservé ÉTAPE 10),
 * SANS vidéos/clips réels (onglets « bientôt », étapes suivantes).
 * États : loading (Skeleton), 404, erreur, contenu.
 */
export default function Channel() {
  const { slug = '' } = useParams<{ slug: string }>()
  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [channel, setChannel] = useState<ChannelDetail | null>(null)
  const [tab, setTab] = useState('videos')
  const [videos, setVideos] = useState<Video[]>([])
  const [clips, setClips] = useState<Clip[]>([])
  const [live, setLive] = useState<StreamDetail | null>(null)
  const [contentsLoading, setContentsLoading] = useState(false)
  const [subscribeOpen, setSubscribeOpen] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStatus('loading')
      setError(null)
      try {
        const detail = await creatorsApi.getBySlug(slug)
        if (!cancelled) {
          setChannel(detail)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) {
          return
        }
        const message = loadError instanceof Error ? loadError.message : ''
        // 404 backend (NOT_FOUND) : chaîne inexistante ou fermée.
        if (message.includes('introuvable')) {
          setStatus('not-found')
        } else {
          setError(message.length > 0 ? message : 'Chargement impossible')
          setStatus('error')
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [slug])

  // Contenus de la chaîne (étape 15) : VOD + clips publics.
  useEffect(() => {
    if (channel === null || (tab !== 'videos' && tab !== 'clips')) return
    let cancelled = false
    async function loadContents() {
      setContentsLoading(true)
      try {
        if (tab === 'videos') {
          const page = await videosApi.list({ creatorId: channel?.id, page: 0, size: 12 })
          if (!cancelled) setVideos(page.content)
        } else {
          const page = await clipsApi.list({ creatorId: channel?.id, page: 0, size: 12 })
          if (!cancelled) setClips(page.content)
        }
      } catch {
        // Contenus indisponibles : la chaîne reste affichée.
      } finally {
        if (!cancelled) setContentsLoading(false)
      }
    }
    void loadContents()
    return () => {
      cancelled = true
    }
  }, [channel, tab])

  // Direct en cours de la chaîne (remplace l'ancien emplacement réservé) :
  // bannière cliquable vers /live/:id quand il existe, rien sinon.
  useEffect(() => {
    if (channel === null) return
    let cancelled = false
    streamsApi
      .byCreator(channel.id, 'LIVE', 0, 1)
      .then((page) => {
        if (!cancelled) setLive(page.content[0] ?? null)
      })
      .catch(() => {
        if (!cancelled) setLive(null)
      })
    return () => {
      cancelled = true
    }
  }, [channel])

  if (status === 'loading') {
    return (
      <div className="mx-auto w-full max-w-5xl space-y-4" aria-busy="true" aria-label="Chargement de la chaîne">
        <Skeleton className="aspect-[16/6] w-full rounded-xl" />
        <div className="flex items-center gap-4">
          <Skeleton className="h-20 w-20 rounded-full" />
          <Skeleton className="h-6 w-48" />
        </div>
        <Skeleton className="h-40 w-full rounded-xl" />
      </div>
    )
  }

  if (status === 'not-found') {
    return (
      <div className="mx-auto w-full max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">404 — Chaîne introuvable</p>
          <p className="mt-1 text-sm text-text-secondary">
            Cette chaîne n'existe pas, plus, ou a été fermée (@{slug}).
          </p>
        </Card>
      </div>
    )
  }

  if (status === 'error' || channel === null) {
    return (
      <div className="mx-auto w-full max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">Chaîne inaccessible</p>
          <p className="mt-1 text-sm text-text-secondary">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      </div>
    )
  }

  const banner = mediaUrl(channel.bannerUrl)
  // L'API chaîne n'expose que la catégorie (ni site ni réseaux : fidélité
  // stricte au backend, aucun champ fantôme).
  const links: { label: string; href: string | null }[] =
    channel.primaryCategory != null
      ? [{ label: `Catégorie : ${channel.primaryCategory.name}`, href: null }]
      : []

  return (
    <div className="mx-auto w-full max-w-5xl space-y-6">
      {/* En-tête façon Twitch : bannière, avatar chevauchant, actions */}
      <section aria-label={`Chaîne ${channel.displayName}`}>
        {banner !== null ? (
          <img
            src={banner}
            alt={`Bannière de ${channel.displayName}`}
            className="aspect-[16/6] w-full rounded-xl border border-border-default object-cover sm:aspect-[21/6]"
          />
        ) : (
          <div
            aria-hidden="true"
            className="aspect-[16/6] w-full rounded-xl border border-border-default bg-[radial-gradient(ellipse_at_top,var(--color-bg-hover)_0%,var(--color-bg-secondary)_70%)] sm:aspect-[21/6]"
          />
        )}
        <div className="flex flex-col gap-4 px-1 sm:flex-row sm:items-end">
          <Avatar
            src={mediaUrl(channel.avatarUrl) ?? undefined}
            name={channel.displayName}
            size="xl"
            className="-mt-10 shrink-0 ring-4 ring-bg-primary"
          />
          <div className="min-w-0 flex-1 sm:pb-1">
            <h1 className="flex flex-wrap items-center gap-2 font-display text-2xl font-bold tracking-tight text-text-primary">
              {channel.displayName}
              {channel.primaryCategory != null && (
                <Badge>{channel.primaryCategory.name}</Badge>
              )}
            </h1>
            <p className="mt-0.5 text-sm text-text-muted">
              @{channel.slug} · {formatCount(channel.followerCount)} abonné
              {channel.followerCount > 1 ? 's' : ''}
            </p>
            {/* Stats mobiles maquette (données réelles uniquement :
                abonnés API + contenus chargés de la chaîne) */}
            <div className="mt-3 grid grid-cols-3 gap-2 text-center md:hidden">
              <div className="rounded-xl border border-border-default bg-bg-surface px-2 py-2.5">
                <p className="font-display text-base font-bold text-text-primary">
                  {formatCount(channel.followerCount)}
                </p>
                <p className="mt-0.5 text-[11px] text-text-muted">Abonnés</p>
              </div>
              <div className="rounded-xl border border-border-default bg-bg-surface px-2 py-2.5">
                <p className="font-display text-base font-bold text-text-primary">
                  {formatCount(videos.length)}
                </p>
                <p className="mt-0.5 text-[11px] text-text-muted">Vidéos</p>
              </div>
              <div className="rounded-xl border border-border-default bg-bg-surface px-2 py-2.5">
                <p className="flex items-center justify-center gap-1.5 font-display text-base font-bold text-text-primary">
                  <span
                    aria-hidden="true"
                    className={live !== null ? 'h-2 w-2 animate-pulse-live rounded-full bg-danger' : 'h-2 w-2 rounded-full bg-text-muted'}
                  />
                  {live !== null ? 'En direct' : 'Hors ligne'}
                </p>
                <p className="mt-0.5 text-[11px] text-text-muted">Statut</p>
              </div>
            </div>
          </div>
          <div className="grid shrink-0 grid-cols-2 gap-2 sm:pb-1 md:flex md:items-center">
            <FollowButton channelId={channel.id} channelName={channel.displayName} />
            <button
              type="button"
              onClick={() => setSubscribeOpen(true)}
              className="flex h-9 items-center rounded-lg border border-border-default bg-bg-elevated px-4 text-sm font-semibold text-text-secondary transition-colors duration-200 hover:border-border-hover hover:text-text-primary"
            >
              S'abonner
            </button>
          </div>
        </div>
        {channel.bio && <p className="mt-3 px-1 text-sm text-text-secondary">{channel.bio}</p>}
        {links.length > 0 && (
          <ul className="mt-3 flex flex-wrap gap-2 px-1 text-sm" aria-label="Informations publiques">
            {links.map((link) => (
              <li
                key={link.label}
                className="rounded-full border border-border-default px-3 py-1 text-text-secondary"
              >
                {link.href !== null ? (
                  <a href={link.href} target="_blank" rel="noreferrer noopener">
                    {link.label}
                  </a>
                ) : (
                  link.label
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Direct en cours — bannière cliquable, rien si hors ligne */}
      {live !== null && (
        <Link
          to={`/live/${live.id}`}
          className="group flex items-center gap-4 rounded-xl border border-primary-500/40 bg-primary-500/10 p-4 transition-colors duration-200 hover:border-primary-500 hover:bg-primary-500/15"
        >
          <LiveBadge size="md" />
          <span className="min-w-0 flex-1">
            <span className="block truncate font-semibold text-text-primary transition-colors duration-200 group-hover:text-primary-300">
              {live.title}
            </span>
            <span className="mt-0.5 block text-sm text-text-secondary">
              <ViewerCount viewers={live.viewerCount} /> en ce moment — clique pour rejoindre
            </span>
          </span>
        </Link>
      )}

      <section aria-label="Contenus">
        <Tabs items={TABS} activeKey={tab} onChange={setTab} />
        <div className="mt-4">
          {tab === 'about' && (
            <Card>
              <p className="text-sm text-text-secondary">
                {channel.bio ?? 'Ce créateur n\'a pas encore écrit de description.'}
              </p>
              <p className="mt-2 text-xs text-text-muted">
                Membre depuis {new Date(channel.createdAt).toLocaleDateString('fr-FR')}
              </p>
            </Card>
          )}
          {tab === 'videos' && (
            contentsLoading ? (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Chargement des vidéos">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="aspect-video w-full rounded-xl" />
                ))}
              </div>
            ) : videos.length === 0 ? (
              <EmptyState
                icon="film"
                title="Aucune vidéo pour le moment"
                description={`${channel.displayName} n'a pas encore publié de VOD. Reviens bientôt !`}
              />
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {videos.map((video) => (
                  <VideoCard key={video.id} video={video} channelName={channel.displayName} />
                ))}
              </div>
            )
          )}
          {tab === 'lives' && (
            live !== null ? (
              <Link
                to={`/live/${live.id}`}
                className="group flex items-center gap-4 rounded-xl border border-primary-500/40 bg-primary-500/10 p-4 transition-colors duration-200 active:bg-primary-500/15 hover:border-primary-500 hover:bg-primary-500/15"
              >
                <LiveBadge size="md" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-semibold text-text-primary transition-colors duration-200 group-hover:text-primary-300">
                    {live.title}
                  </span>
                  <span className="mt-0.5 block text-sm text-text-secondary">
                    <ViewerCount viewers={live.viewerCount} /> en ce moment — clique pour rejoindre
                  </span>
                </span>
              </Link>
            ) : (
              <EmptyState
                icon="video"
                title="Hors ligne pour le moment"
                description={`${channel.displayName} n'est pas en direct. Suis la chaîne pour être alerté du prochain live.`}
              />
            )
          )}
          {tab === 'clips' && (
            contentsLoading ? (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true" aria-label="Chargement des clips">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="aspect-video w-full rounded-xl" />
                ))}
              </div>
            ) : clips.length === 0 ? (
              <EmptyState
                icon="film"
                title="Aucun clip pour le moment"
                description="Les meilleurs moments de cette chaîne apparaîtront ici."
              />
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {clips.map((clip) => (
                  <ClipCard key={clip.id} clip={clip} />
                ))}
              </div>
            )
          )}
        </div>
      </section>

      <SubscribeModal
        open={subscribeOpen}
        onClose={() => setSubscribeOpen(false)}
        channelName={channel.displayName}
      />
    </div>
  )
}
