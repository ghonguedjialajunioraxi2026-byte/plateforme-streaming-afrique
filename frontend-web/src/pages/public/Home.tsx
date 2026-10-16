import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { MapPin } from '@phosphor-icons/react'
import { MobileLiveCard, MobileCategoryTile, MobileSectionHeader } from '../../components/mobile'
import { FlickSection } from '../../components/flick'
import { LiveCard, LiveCardSkeleton } from '../../components/streaming'
import { EmptyState } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'
import { APP_DESCRIPTION } from '../../constants/app'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import { categoriesApi, type Category } from '../../services/api/categories'
import { regionApi, type EmergingCreator, type Trending } from '../../services/api/region'
import type { Stream } from '../../types/domain'

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
      startedAt: detail.startedAt ?? undefined,
    },
    channelName: detail.creatorSlug ?? undefined,
  }
}

/**
 * Accueil FLICK — données RÉELLES uniquement (aucun mock) :
 * lives (`GET /streams?status=LIVE`), catégories (`GET /categories`),
 * créateurs émergents + tendances (`GET /discovery/...`).
 * Base vide → états vides propres (jamais de contenu inventé).
 */
export default function Home() {
  usePageMeta('Accueil', APP_DESCRIPTION)

  const [lives, setLives] = useState<StreamDetail[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [emerging, setEmerging] = useState<EmergingCreator[]>([])
  const [trending, setTrending] = useState<Trending | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setFailed(false)
      try {
        const [livePage, categoryPage, emergingCreators, trend] = await Promise.all([
          streamsApi.listLive(0, 8),
          categoriesApi.list(undefined, 0, 8),
          regionApi.emerging({ size: 4 }).catch(() => [] as EmergingCreator[]),
          regionApi.trending({ size: 6 }).catch(() => null),
        ])
        if (cancelled) return
        setLives(livePage.content)
        setCategories(categoryPage.content)
        setEmerging(emergingCreators)
        setTrending(trend)
      } catch {
        if (!cancelled) setFailed(true)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  const heroLive = lives[0] ?? null
  const heroCard = heroLive ? toCardStream(heroLive) : null

  return (
    <>
      {/* ============ MOBILE ============ */}
      <div className="mx-auto w-full max-w-xl space-y-6 md:hidden">
        {loading ? (
          <div className="space-y-3" aria-busy="true" aria-label="Chargement de l'accueil">
            <div className="aspect-video w-full animate-pulse rounded-xl bg-bg-elevated" />
            <div className="grid grid-cols-3 gap-2">
              {[0, 1, 2].map((i) => (
                <div key={i} className="aspect-video animate-pulse rounded-lg bg-bg-elevated" />
              ))}
            </div>
          </div>
        ) : failed ? (
          <EmptyState
            icon="video"
            title="Contenu indisponible"
            description="Impossible de charger l'accueil. Vérifie ta connexion puis réessaie."
          />
        ) : (
          <>
            {heroCard && (
              <Link
                to={`/live/${heroCard.stream.id}`}
                className="block min-w-0 active:opacity-90"
                aria-label={`Regarder ${heroCard.stream.title} en direct`}
              >
                <div className="relative aspect-video overflow-hidden rounded-2xl border border-border-default bg-bg-elevated">
                  {heroCard.stream.thumbnailUrl ? (
                    <img src={heroCard.stream.thumbnailUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
                  ) : (
                    <div
                      aria-hidden="true"
                      className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-accent-500)_0%,var(--color-bg-hover)_50%,var(--color-bg-secondary)_100%)] text-6xl"
                    >
                      🎬
                    </div>
                  )}
                  <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-black/10" />
                  <div className="absolute inset-x-2.5 top-2.5 flex items-center justify-between">
                    <span className="flex items-center gap-1 rounded-md bg-danger px-2 py-1 text-[11px] font-bold tracking-wide text-white uppercase">
                      <span aria-hidden="true" className="h-1.5 w-1.5 animate-pulse-live rounded-full bg-white" />
                      Live
                    </span>
                    <span className="rounded-md bg-black/70 px-2 py-1 text-[11px] font-semibold text-white">
                      {heroCard.stream.viewersCount} spectateurs
                    </span>
                  </div>
                  <div className="absolute inset-x-3 bottom-3">
                    <p className="line-clamp-1 text-sm font-bold text-white">{heroCard.stream.title}</p>
                    <p className="mt-0.5 line-clamp-1 text-xs text-white/70">
                      {heroCard.channelName ?? ''}{heroCard.stream.category ? ` · ${heroCard.stream.category}` : ''}
                    </p>
                  </div>
                </div>
              </Link>
            )}
            <section aria-label="Lives en direct">
              <MobileSectionHeader title="Lives en direct" seeAllTo="/explorer" />
              {lives.length === 0 ? (
                <EmptyState
                  icon="video"
                  title="Aucun stream en direct"
                  description="Personne n'est en live pour le moment."
                />
              ) : (
                <div className="mt-3 grid grid-cols-3 gap-x-2 gap-y-4">
                  {lives.slice(0, 6).map((detail) => {
                    const card = toCardStream(detail)
                    return (
                      <MobileLiveCard
                        key={detail.id}
                        stream={card.stream}
                        channelName={card.channelName}
                        variant="compact"
                        verified
                      />
                    )
                  })}
                </div>
              )}
            </section>

            <section aria-label="Catégories populaires">
              <MobileSectionHeader title="Catégories" seeAllTo="/categories" />
              {categories.length === 0 ? (
                <EmptyState
                  icon="grid"
                  title="Aucune catégorie"
                  description="Les catégories apparaîtront ici dès leur création."
                />
              ) : (
                <div className="mt-3 grid grid-cols-4 gap-2">
                  {categories.slice(0, 4).map((category) => (
                    <MobileCategoryTile
                      key={category.id}
                      category={{
                        id: String(category.id),
                        name: category.name,
                        slug: category.slug,
                        viewersCount: 0,
                        streamsCount: 0,
                      }}
                    />
                  ))}
                </div>
              )}
            </section>

            <section aria-label="Créateurs émergents">
              <MobileSectionHeader title="Créateurs émergents" seeAllTo="/explorer" />
              {emerging.length === 0 ? (
                <EmptyState
                  icon="users"
                  title="Aucun créateur à mettre en avant"
                  description="Les créateurs émergents apparaîtront ici dès qu'ils lanceront leurs premiers lives."
                />
              ) : (
                <div className="mt-3 space-y-2">
                  {emerging.slice(0, 4).map((creator) => (
                    <Link
                      key={creator.id}
                      to={`/channel/${creator.slug}`}
                      className="block rounded-xl border border-border-default bg-bg-surface p-3 text-sm font-semibold text-text-primary"
                    >
                      {creator.displayName}
                    </Link>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
      </div>

      {/* ============ DESKTOP ============ */}
      <div className="mx-auto hidden w-full max-w-[1600px] space-y-10 md:block">
        {loading ? (
          <div className="grid grid-cols-1 gap-x-4 gap-y-7 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4" aria-busy="true" aria-label="Chargement de l'accueil">
            {[0, 1, 2, 3].map((i) => (
              <LiveCardSkeleton key={i} />
            ))}
          </div>
        ) : failed ? (
          <EmptyState
            icon="video"
            title="Contenu indisponible"
            description="Impossible de charger l'accueil. Vérifie ta connexion puis réessaie."
          />
        ) : (
          <>
            <FlickSection
              title="Lives actuellement en direct"
              subtitle="Les streams qui font vibrer la communauté en ce moment"
              seeAllTo="/explorer"
            >
              {lives.length === 0 ? (
                <EmptyState
                  icon="video"
                  title="Aucun stream en direct"
                  description="Personne n'est en live pour le moment. Explore les catégories pour trouver ton prochain direct."
                  action={
                    <Link
                      to="/categories"
                      className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
                    >
                      Parcourir les catégories
                    </Link>
                  }
                />
              ) : (
                <div className="grid grid-cols-1 gap-x-4 gap-y-7 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
                  {lives.map((detail) => {
                    const card = toCardStream(detail)
                    return <LiveCard key={detail.id} stream={card.stream} channelName={card.channelName} />
                  })}
                </div>
              )}
            </FlickSection>

            <FlickSection
              title="Catégories"
              subtitle="Choisis ton univers"
              seeAllTo="/categories"
            >
              {categories.length === 0 ? (
                <EmptyState
                  icon="grid"
                  title="Aucune catégorie"
                  description="Les catégories apparaîtront ici dès leur création."
                />
              ) : (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 2xl:grid-cols-8">
                  {categories.map((category) => (
                    <Link
                      key={category.id}
                      to={`/categories?search=${encodeURIComponent(category.name)}`}
                      className="group rounded-xl border border-border-default bg-bg-surface p-4 transition-all hover:border-primary-500/40 hover:shadow-glow-primary"
                    >
                      <p className="text-sm font-bold text-text-primary group-hover:text-primary-300">
                        {category.name}
                      </p>
                      {category.description && (
                        <p className="mt-1 line-clamp-2 text-xs text-text-muted">{category.description}</p>
                      )}
                    </Link>
                  ))}
                </div>
              )}
            </FlickSection>

            <FlickSection
              title="Créateurs émergents"
              subtitle="Les nouvelles voix à suivre de près"
              seeAllTo="/explorer"
            >
              {emerging.length === 0 ? (
                <EmptyState
                  icon="users"
                  title="Aucun créateur à mettre en avant"
                  description="Les créateurs émergents apparaîtront ici dès qu'ils lanceront leurs premiers lives."
                />
              ) : (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-4">
                  {emerging.map((creator) => (
                    <Link
                      key={creator.id}
                      to={`/channel/${creator.slug}`}
                      className="group rounded-xl border border-border-default bg-bg-surface p-4 transition-all hover:border-primary-500/40"
                    >
                      <p className="font-bold text-text-primary group-hover:text-primary-300">
                        {creator.displayName}
                      </p>
                      <p className="mt-1 text-xs text-text-muted">{creator.followerCount} abonnés</p>
                    </Link>
                  ))}
                </div>
              )}
            </FlickSection>

            {trending && (trending.streams.length > 0 || trending.videos.length > 0) && (
              <FlickSection
                title="Tendances en Afrique"
                subtitle="Contenus géolocalisés par pays"
                seeAllTo="/explorer"
              >
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                  {trending.streams.slice(0, 6).map(({ stream }) => (
                    <Link
                      key={stream.id}
                      to={`/live/${stream.id}`}
                      className="group rounded-xl border border-border-default bg-bg-surface p-4 transition-all hover:border-primary-500/40 hover:shadow-glow-primary"
                    >
                      <p className="flex items-center gap-1.5 text-sm font-bold text-text-primary group-hover:text-primary-300">
                        <MapPin size={15} className="text-primary-400" />
                        <span className="line-clamp-1">{stream.title}</span>
                      </p>
                      <p className="mt-2 text-2xl font-black text-text-primary">{stream.viewerCount}</p>
                      <p className="text-xs text-text-muted">spectateurs</p>
                    </Link>
                  ))}
                </div>
              </FlickSection>
            )}
          </>
        )}
      </div>
    </>
  )
}
