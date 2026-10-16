import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CreatorCard, LiveCard, LiveCardSkeleton } from '../../components/streaming'
import { MobileChip, MobileChipRail, MobileVideoRow } from '../../components/mobile'
import { ClipCard, VideoCard } from '../../components/video'
import { Button, EmptyState, Input, Skeleton } from '../../components/ui'
import { formatCount } from '../../utils/format'
import { SectionHeader } from '../../components/common/SectionHeader'
import { creatorsApi } from '../../services/api/creators'
import { videosApi, type Video } from '../../services/api/videos'
import { clipsApi } from '../../services/api/clips'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import type { Clip } from '../../services/api/videos'
import type { ChannelDetail, Stream } from '../../types/domain'

/** `StreamDetail` serveur → carte live d'affichage. */
function toLiveCardStream(detail: StreamDetail): Stream {
  return {
    id: String(detail.id),
    channelId: String(detail.creatorId),
    title: detail.title,
    category: detail.categoryName ?? 'Sans catégorie',
    viewersCount: detail.viewerCount,
    status: 'LIVE',
    thumbnailUrl: detail.thumbnailUrl ?? undefined,
    language: detail.language ?? 'fr',
  }
}

function GroupSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title}>
      <SectionHeader title={title} className="mb-3" />
      {children}
    </section>
  )
}

/**
 * Recherche façon Twitch : UNE page, résultats groupés
 * (chaînes / en direct / vidéos / clips), skeletons par groupe,
 * état vide global travaillé. Les lives sont filtrés côté client
 * (l'endpoint `listLive` n'expose pas de recherche plein-texte).
 */
export default function Search() {
  const [searchParams, setSearchParams] = useSearchParams()
  const initialQuery = searchParams.get('q') ?? ''
  const [query, setQuery] = useState(initialQuery)
  const [videos, setVideos] = useState<Video[]>([])
  const [clips, setClips] = useState<Clip[]>([])
  const [channels, setChannels] = useState<ChannelDetail[]>([])
  const [lives, setLives] = useState<Stream[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const [mobileTab, setMobileTab] = useState<'all' | 'channels' | 'lives' | 'videos' | 'clips'>('all')

  async function runSearch(text: string) {
    const trimmed = text.trim()
    if (trimmed.length === 0) return
    setLoading(true)
    try {
      const [videoPage, clipPage, channelPage, livePage] = await Promise.all([
        videosApi.list({ search: trimmed, page: 0, size: 8 }),
        clipsApi.list({ search: trimmed, page: 0, size: 8 }),
        creatorsApi.list(trimmed, 0, 8),
        streamsApi.listLive(0, 12).catch(() => null),
      ])
      setVideos(videoPage.content)
      setClips(clipPage.content)
      setChannels(channelPage.content)
      const needle = trimmed.toLowerCase()
      setLives(
        (livePage?.content ?? [])
          .filter(
            (live) =>
              live.title.toLowerCase().includes(needle) ||
              (live.categoryName ?? '').toLowerCase().includes(needle),
          )
          .slice(0, 4)
          .map(toLiveCardStream),
      )
      setTotal(
        videoPage.totalElements +
          clipPage.totalElements +
          channelPage.totalElements +
          (livePage?.totalElements ?? 0),
      )
      setSearched(true)
    } catch {
      setVideos([])
      setClips([])
      setChannels([])
      setLives([])
      setTotal(0)
      setSearched(true)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (initialQuery.trim().length > 0) {
      void runSearch(initialQuery)
    }
    // Recherche initiale uniquement (le formulaire pilote la suite).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setSearchParams(query.trim().length > 0 ? { q: query.trim() } : {})
    void runSearch(query)
  }

  const empty =
    searched &&
    !loading &&
    lives.length === 0 &&
    channels.length === 0 &&
    videos.length === 0 &&
    clips.length === 0

  return (
    <div className="mx-auto max-w-6xl space-y-8 p-6">
      <h1 className="font-display text-2xl font-bold tracking-tight text-text-primary">
        Recherche
      </h1>
      <form onSubmit={handleSubmit} className="flex gap-2" role="search">
        <Input
          name="q"
          placeholder="Vidéos, clips, chaînes…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Rechercher"
        />
        <Button type="submit" variant="primary" loading={loading}>
          Chercher
        </Button>
      </form>

      {loading && (
        <div className="space-y-8" aria-busy="true" aria-label="Recherche en cours">
          {[0, 1].map((section) => (
            <div key={section} className="space-y-3">
              <Skeleton className="h-6 w-40" />
              <div className="grid gap-x-3 gap-y-6 sm:grid-cols-2 lg:grid-cols-4">
                {[0, 1, 2, 3].map((i) => (
                  <LiveCardSkeleton key={i} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && searched && !empty && (
        <p className="text-sm text-text-secondary" aria-live="polite">
          {total} résultat{total > 1 ? 's' : ''} pour « {searchParams.get('q') ?? query} »
        </p>
      )}

      {!loading && empty && (
        <EmptyState
          icon="search"
          title="Aucun résultat"
          description={`Rien trouvé pour « ${searchParams.get('q') ?? query} ». Vérifie l'orthographe ou explore les catégories pour découvrir des lives.`}
        />
      )}

      {!loading && searched && !empty && (
        <>
          {/* ============ MOBILE — liste verticale maquette ============ */}
          <div className="space-y-1 md:hidden">
            <MobileChipRail label="Filtrer les résultats">
              <MobileChip label="Tout" active={mobileTab === 'all'} onClick={() => setMobileTab('all')} />
              <MobileChip label="Chaînes" active={mobileTab === 'channels'} onClick={() => setMobileTab('channels')} />
              <MobileChip label="Lives" active={mobileTab === 'lives'} onClick={() => setMobileTab('lives')} />
              <MobileChip label="Vidéos" active={mobileTab === 'videos'} onClick={() => setMobileTab('videos')} />
              <MobileChip label="Clips" active={mobileTab === 'clips'} onClick={() => setMobileTab('clips')} />
            </MobileChipRail>
            <div className="divide-y divide-border-default">
              {(mobileTab === 'all' || mobileTab === 'channels') &&
                channels.map((channel) => (
                  <Link
                    key={`m-chan-${channel.id}`}
                    to={`/channel/${channel.slug}`}
                    className="flex min-h-11 items-center gap-3 py-2.5 active:opacity-80"
                  >
                    <span
                      aria-hidden="true"
                      className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-bg-elevated text-base font-bold text-primary-400"
                    >
                      {channel.displayName.charAt(0).toUpperCase()}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-semibold text-text-primary">
                        {channel.displayName}{' '}
                        <span aria-label="Chaîne vérifiée" className="text-xs text-primary-400">✓</span>
                      </span>
                      <span className="block truncate text-[13px] text-text-muted">
                        Chaîne · {formatCount(channel.followerCount)} abonnés
                      </span>
                    </span>
                  </Link>
                ))}
              {(mobileTab === 'all' || mobileTab === 'lives') &&
                lives.map((live) => (
                  <MobileVideoRow
                    key={`m-live-${live.id}`}
                    id={live.id}
                    kind="live"
                    title={live.title}
                    channelName={typeof live.channelId === 'string' && live.channelId.length > 0 && !/^\d+$/.test(live.channelId) ? live.channelId : 'En direct'}
                    views={live.viewersCount}
                    thumbnailUrl={live.thumbnailUrl}
                  />
                ))}
              {(mobileTab === 'all' || mobileTab === 'videos') &&
                videos.map((video) => (
                  <MobileVideoRow
                    key={`m-vid-${video.id}`}
                    id={String(video.id)}
                    kind="video"
                    title={video.title}
                    channelName={`Chaîne #${video.creatorId}`}
                    views={video.viewCount}
                    thumbnailUrl={video.thumbnailUrl}
                    durationSeconds={video.durationSeconds}
                  />
                ))}
              {(mobileTab === 'all' || mobileTab === 'clips') &&
                clips.map((clip) => (
                  <MobileVideoRow
                    key={`m-clip-${clip.id}`}
                    id={String(clip.id)}
                    kind="clip"
                    title={clip.title}
                    channelName="Clip"
                    views={clip.viewCount}
                    thumbnailUrl={clip.thumbnailUrl}
                    durationSeconds={clip.durationSeconds}
                    badge="Clip"
                  />
                ))}
            </div>
          </div>

          {/* ============ DESKTOP — groupes inchangés ============ */}
          <div className="hidden md:contents">
          {channels.length > 0 && (
            <GroupSection title="Chaînes">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {channels.map((channel) => (
                  <CreatorCard
                    key={channel.id}
                    channel={{
                      id: String(channel.id),
                      userId: String(channel.userId),
                      name: channel.displayName,
                      slug: channel.slug,
                      description: channel.bio ?? undefined,
                      followersCount: channel.followerCount,
                      isLive: false,
                    }}
                  />
                ))}
              </div>
            </GroupSection>
          )}

          {lives.length > 0 && (
            <GroupSection title="En direct">
              <div className="grid gap-x-3 gap-y-6 sm:grid-cols-2 lg:grid-cols-4">
                {lives.map((live) => (
                  <LiveCard key={live.id} stream={live} />
                ))}
              </div>
            </GroupSection>
          )}

          {videos.length > 0 && (
            <GroupSection title="Vidéos">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {videos.map((video) => (
                  <VideoCard key={video.id} video={video} />
                ))}
              </div>
            </GroupSection>
          )}

          {clips.length > 0 && (
            <GroupSection title="Clips">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {clips.map((clip) => (
                  <ClipCard key={clip.id} clip={clip} />
                ))}
              </div>
            </GroupSection>
          )}
          </div>
        </>
      )}
    </div>
  )
}
