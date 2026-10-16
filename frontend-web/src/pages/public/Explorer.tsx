import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { FunnelSimple } from '@phosphor-icons/react'
import { LiveCard } from '../../components/streaming'
import { FlickSection } from '../../components/flick'
import { MobileChip, MobileChipRail, MobileLiveCard } from '../../components/mobile'
import { ClipCard, VideoCard } from '../../components/video'
import { Card, Skeleton, BottomSheet } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { CountrySelector, LanguageFilter } from '../../features/region/RegionSelectors'
import { useI18n } from '../../i18n'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import { videosApi, type Video } from '../../services/api/videos'
import { clipsApi } from '../../services/api/clips'
import { regionApi, type Country, type Trending } from '../../services/api/region'
import type { Clip } from '../../services/api/videos'
import type { Stream } from '../../types/domain'

type ExplorerFilter = 'all' | 'lives' | 'videos' | 'clips'

/** `StreamDetail` serveur → carte d'affichage (mobile + desktop). */
function toCardStream(detail: StreamDetail): { stream: Stream; channelName?: string } {
  return {
    stream: {
      id: String(detail.id),
      channelId: String(detail.creatorId),
      title: detail.title,
      category: detail.categoryName ?? '',
      viewersCount: detail.viewerCount,
      status: 'LIVE',
      thumbnailUrl: detail.thumbnailUrl ?? undefined,
      language: detail.language ?? '',
    },
    channelName: detail.creatorSlug ?? undefined,
  }
}

/// Explorer (étapes 15 + 19) : en direct + vidéos + clips + tendances
/// Africa+. Filtres pays/langue issus des données (jamais hard-codés).
export default function Explorer() {
  const { t } = useI18n()
  const [lives, setLives] = useState<StreamDetail[]>([])
  const [videos, setVideos] = useState<Video[]>([])
  const [clips, setClips] = useState<Clip[]>([])
  const [trending, setTrending] = useState<Trending | null>(null)
  const [countries, setCountries] = useState<Country[]>([])
  const [locales, setLocales] = useState<string[]>([])
  const [country, setCountry] = useState('')
  const [language, setLanguage] = useState('')
  const [category, setCategory] = useState('')
  const [liveOnly, setLiveOnly] = useState(false)
  const [loading, setLoading] = useState(true)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [filter, setFilter] = useState<ExplorerFilter>('all')
  const activeFilterCount = (country ? 1 : 0) + (language ? 1 : 0) + (category ? 1 : 0) + (liveOnly ? 1 : 0)

  const load = useCallback(async (countryCode: string, lang: string) => {
    setLoading(true)
    try {
      const [livePage, videoFeed, clipFeed, trend] = await Promise.all([
        streamsApi.listLive(0, 8, lang || undefined),
        videosApi.feed(undefined, undefined, 0, 8, lang || undefined),
        clipsApi.feed(undefined, 0, 8),
        regionApi.trending({
          country: countryCode || undefined,
          language: lang || undefined,
          size: 8,
        }).catch(() => null),
      ])
      setLives(livePage.content)
      setVideos(videoFeed.content)
      setClips(clipFeed.content)
      setTrending(trend)
    } catch {
      // Sections indisponibles : page partielle plutôt que vide.
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    regionApi.countries().then(setCountries).catch(() => undefined)
    regionApi.locales().then(setLocales).catch(() => undefined)
    void load('', '')
  }, [load])

  useEffect(() => {
    void load(country, language)
  }, [country, language, load])

  const visibleLives = useMemo(() => {
    if (!category) return lives
    const needle = category.toLowerCase()
    return lives.filter((s) => (s.categoryName ?? '').toLowerCase().includes(needle))
  }, [lives, category])

  const showVideos = !liveOnly && (filter === 'all' || filter === 'videos')
  const showClips = !liveOnly && (filter === 'all' || filter === 'clips')

  if (loading && lives.length === 0 && videos.length === 0) {
    return (
      <div className="mx-auto max-w-6xl space-y-8 p-6" aria-busy="true" aria-label="Chargement de l'explorer">
        {[0, 1].map((section) => (
          <div key={section} className="space-y-3">
            <Skeleton className="h-7 w-48 rounded" />
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              {[0, 1, 2, 3].map((i) => (
                <Skeleton key={i} className="aspect-video w-full rounded-xl" />
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-8 p-4 lg:p-6">
      <FlickSection title="Explorer" subtitle="Filtre par catégorie, pays, langue et statut LIVE">
        <div className="flex flex-wrap items-center gap-2">
          {/* Filtres inline sur desktop */}
          <div className="hidden flex-wrap items-center gap-2 md:flex">
            <div className="w-48">
              <CountrySelector countries={countries} value={country} onChange={setCountry} includeAll />
            </div>
            <div className="w-40">
              <LanguageFilter languages={locales} value={language} onChange={setLanguage} />
            </div>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              aria-label="Filtrer par catégorie"
              className="h-10 rounded-lg border border-border-default bg-bg-surface px-3 text-sm text-text-primary"
            >
              <option value="">Toutes catégories</option>
              <option value="Gaming">Gaming</option>
              <option value="Musique">Musique</option>
              <option value="Sport">Sport</option>
              <option value="Discussion">Discussion</option>
              <option value="Podcast">Podcast</option>
              <option value="Éducation">Éducation</option>
              <option value="Technologie">Technologie</option>
              <option value="IRL">IRL</option>
            </select>
            <button
              type="button"
              onClick={() => setLiveOnly((v) => !v)}
              aria-pressed={liveOnly}
              className={`flex h-10 items-center gap-1.5 rounded-lg border px-3 text-sm font-semibold transition-colors ${liveOnly ? 'border-danger/60 bg-danger/10 text-danger' : 'border-border-default bg-bg-surface text-text-secondary hover:text-text-primary'}`}
            >
              <span className={`h-2 w-2 rounded-full ${liveOnly ? 'animate-pulse-live bg-live' : 'bg-text-muted'}`} />
              LIVE uniquement
            </button>
            {activeFilterCount > 0 && (
              <button
                type="button"
                onClick={() => {
                  setCountry('')
                  setLanguage('')
                  setCategory('')
                  setLiveOnly(false)
                }}
                className="flex h-10 items-center rounded-lg px-3 text-sm font-medium text-text-muted hover:text-text-primary"
              >
                Réinitialiser
              </button>
            )}
          </div>
        </div>
      </FlickSection>
      {/* Filtres en bottom sheet sur mobile */}
      <button
        type="button"
        onClick={() => setFiltersOpen(true)}
        aria-haspopup="dialog"
        className="flex h-10 items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-3 text-sm font-semibold text-text-secondary transition-colors active:border-border-hover active:text-text-primary md:hidden"
      >
        <span className="flex items-center gap-2">
          <FunnelSimple size={16} />
          Filtres
        </span>
        {activeFilterCount > 0 && (
          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-primary-500 px-1.5 text-[11px] font-bold text-text-inverse">
            {activeFilterCount}
          </span>
        )}
      </button>

      <BottomSheet open={filtersOpen} onClose={() => setFiltersOpen(false)} title="Filtres">
        <div className="space-y-4 pb-2">
          <div>
            <p className="mb-1.5 text-xs font-bold tracking-wider text-text-muted uppercase">Pays</p>
            <CountrySelector countries={countries} value={country} onChange={setCountry} includeAll />
          </div>
          <div>
            <p className="mb-1.5 text-xs font-bold tracking-wider text-text-muted uppercase">Langue</p>
            <LanguageFilter languages={locales} value={language} onChange={setLanguage} />
          </div>
          <div>
            <p className="mb-1.5 text-xs font-bold tracking-wider text-text-muted uppercase">Catégorie</p>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              aria-label="Filtrer par catégorie"
              className="h-11 w-full rounded-lg border border-border-default bg-bg-elevated px-3 text-sm text-text-primary"
            >
              <option value="">Toutes catégories</option>
              <option value="Gaming">Gaming</option>
              <option value="Musique">Musique</option>
              <option value="Sport">Sport</option>
              <option value="Discussion">Discussion</option>
              <option value="Podcast">Podcast</option>
              <option value="Éducation">Éducation</option>
              <option value="Technologie">Technologie</option>
              <option value="IRL">IRL</option>
            </select>
          </div>
          <div className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={() => {
                setCountry('')
                setLanguage('')
                setCategory('')
                setLiveOnly(false)
              }}
              className="flex h-11 flex-1 items-center justify-center rounded-lg border border-border-default text-sm font-semibold text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
            >
              Réinitialiser
            </button>
            <button
              type="button"
              onClick={() => setFiltersOpen(false)}
              className="flex h-11 flex-1 items-center justify-center rounded-lg bg-primary-500 text-sm font-semibold text-text-inverse transition-colors active:bg-primary-400"
            >
              Voir les résultats
            </button>
          </div>
        </div>
      </BottomSheet>

      {/* Mobile maquette : recherche + chips Tout/Lives/Vidéos/Clips */}
      <div className="space-y-3 md:hidden">
        <Link
          to="/recherche"
          aria-label="Rechercher des créateurs, des jeux"
          className="flex h-12 items-center gap-2.5 rounded-xl border border-border-default bg-bg-surface px-4 text-sm text-text-muted transition-colors active:border-border-hover active:text-text-secondary"
        >
          <Icon name="search" className="h-4 w-4" />
          Rechercher des créateurs, des jeux…
        </Link>
        <MobileChipRail label="Filtrer par type de contenu">
          <MobileChip label="Tout" active={filter === 'all'} onClick={() => setFilter('all')} />
          <MobileChip label="Lives" active={filter === 'lives'} onClick={() => setFilter('lives')} />
          <MobileChip label="Vidéos" active={filter === 'videos'} onClick={() => setFilter('videos')} />
          <MobileChip label="Clips" active={filter === 'clips'} onClick={() => setFilter('clips')} />
        </MobileChipRail>
      </div>

      {filter === 'all' && trending !== null && (trending.streams.length > 0 || trending.videos.length > 0) && (
        <section aria-labelledby="trending-title">
          <h2 id="trending-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
            {t('region.trending')}
          </h2>
          {/* Mobile : cartes compactes maquette */}
          <div className="grid grid-cols-2 gap-x-2 gap-y-4 md:hidden">
            {trending.streams.slice(0, 4).map(({ stream }) => (
              <MobileLiveCard
                key={`trend-m-${stream.id}`}
                stream={{
                  id: String(stream.id),
                  channelId: '',
                  title: stream.title,
                  category: stream.categorySlug ?? '',
                  viewersCount: stream.viewerCount,
                  status: 'LIVE',
                  thumbnailUrl: stream.thumbnailUrl ?? undefined,
                  language: stream.language ?? '',
                }}
                channelName={stream.creatorSlug ?? undefined}
                variant="compact"
              />
            ))}
          </div>
          <div className="hidden gap-3 sm:grid-cols-2 md:grid lg:grid-cols-4">
            {trending.streams.slice(0, 4).map(({ stream }) => (
              <LiveCard
                key={`trend-${stream.id}`}
                stream={{
                  id: String(stream.id),
                  channelId: '',
                  title: stream.title,
                  category: stream.categorySlug ?? '',
                  viewersCount: stream.viewerCount,
                  status: 'LIVE',
                  thumbnailUrl: stream.thumbnailUrl ?? undefined,
                  language: stream.language ?? '',
                }}
                channelName={stream.creatorSlug ?? undefined}
              />
            ))}
          </div>
        </section>
      )}

      {(filter === 'all' || filter === 'lives') && (
      <section aria-labelledby="live-title">
        <h2 id="live-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
          En direct{category ? ` — ${category}` : ''}{liveOnly ? ' · LIVE' : ''}
        </h2>
        {visibleLives.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun direct en ce moment{category ? ` dans « ${category} »` : ''}.</p>
          </Card>
        ) : (
          <>
            {/* Mobile : cartes compactes maquette */}
            <div className="grid grid-cols-2 gap-x-2 gap-y-4 md:hidden">
              {visibleLives.map((stream) => {
                const card = toCardStream(stream)
                return (
                  <MobileLiveCard
                    key={`m-${stream.id}`}
                    stream={card.stream}
                    channelName={card.channelName}
                    variant="compact"
                  />
                )
              })}
            </div>
            <div className="hidden gap-3 sm:grid-cols-2 md:grid lg:grid-cols-4">
              {visibleLives.map((stream) => (
                <LiveCard
                  key={stream.id}
                  stream={{
                    id: String(stream.id),
                    channelId: String(stream.creatorId),
                    title: stream.title,
                    category: stream.categoryName ?? '',
                    viewersCount: stream.viewerCount,
                    status: 'LIVE',
                    thumbnailUrl: stream.thumbnailUrl ?? undefined,
                    language: stream.language ?? '',
                  }}
                  channelName={stream.creatorSlug ?? undefined}
                />
              ))}
            </div>
          </>
        )}
      </section>
      )}

      {(showVideos) && (
      <section aria-labelledby="videos-title">
        <h2 id="videos-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
          Vidéos récentes
        </h2>
        {videos.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucune vidéo publiée pour le moment.</p>
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {videos.map((video) => (
              <VideoCard key={video.id} video={video} />
            ))}
          </div>
        )}
      </section>
      )}

      {(showClips) && (
      <section aria-labelledby="clips-title">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="clips-title" className="font-display text-lg font-semibold text-text-primary">
            Nouveaux clips
          </h2>
          <Link to="/recherche" className="text-sm text-primary-400 hover:underline">
            Tout rechercher
          </Link>
        </div>
        {clips.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun clip pour le moment.</p>
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            {clips.map((clip) => (
              <ClipCard key={clip.id} clip={clip} />
            ))}
          </div>
        )}
      </section>
      )}
    </div>
  )
}
