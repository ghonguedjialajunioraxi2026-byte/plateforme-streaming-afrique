import { useCallback, useEffect, useMemo, useState } from 'react'
import { CategoryCard, CategoryCardSkeleton } from '../../components/streaming'
import { MobileCategoryTile } from '../../components/mobile'
import { SectionHeader } from '../../components/common/SectionHeader'
import { EmptyState, Input } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { usePageMeta } from '../../hooks/usePageMeta'
import { categoriesApi } from '../../services/api/categories'
import { streamsApi } from '../../services/api/streams'
import type { Category as DisplayCategory } from '../../types/domain'
import { cn } from '../../utils/cn'

type Status = 'loading' | 'ready' | 'error'
type SortKey = 'popularity' | 'name'

/**
 * Catégories façon Twitch : grille de box-arts, recherche serveur,
 * tri (popularité / A-Z), filtre « en direct uniquement ».
 * Liste = vraies catégories du backend ; compteurs = lives RÉELS
 * en cours par catégorie (`GET /streams?status=LIVE`), jamais inventés.
 */
export default function Categories() {
  usePageMeta('Catégories', 'Parcourez toutes les catégories de contenu disponibles sur FLICK.')

  const [status, setStatus] = useState<Status>('loading')
  const [items, setItems] = useState<DisplayCategory[]>([])
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<SortKey>('popularity')
  const [liveOnly, setLiveOnly] = useState(false)

  const load = useCallback(async (search: string) => {
    setStatus('loading')
    try {
      const [page, livePage] = await Promise.all([
        categoriesApi.list(search.trim() || undefined, 0, 60),
        streamsApi.listLive(0, 100).catch(() => null),
      ])
      const viewersByCategory = new Map<string, { viewers: number; streams: number }>()
      for (const live of livePage?.content ?? []) {
        const key = (live.categoryName ?? '').toLowerCase()
        if (!key) continue
        const current = viewersByCategory.get(key) ?? { viewers: 0, streams: 0 }
        current.viewers += live.viewerCount
        current.streams += 1
        viewersByCategory.set(key, current)
      }
      setItems(
        page.content.map((category) => {
          const audience = viewersByCategory.get(category.name.toLowerCase()) ?? { viewers: 0, streams: 0 }
          return {
            id: String(category.id),
            name: category.name,
            slug: category.slug,
            viewersCount: audience.viewers,
            streamsCount: audience.streams,
          }
        }),
      )
      setStatus('ready')
    } catch {
      setStatus('error')
    }
  }, [])

  // Recherche serveur avec debounce (300ms).
  useEffect(() => {
    const timer = window.setTimeout(() => void load(query), query ? 300 : 0)
    return () => window.clearTimeout(timer)
  }, [query, load])

  const visible = useMemo(() => {
    const filtered = liveOnly ? items.filter((item) => item.viewersCount > 0) : [...items]
    filtered.sort((a, b) =>
      sort === 'name'
        ? a.name.localeCompare(b.name, 'fr')
        : b.viewersCount - a.viewersCount || a.name.localeCompare(b.name, 'fr'),
    )
    return filtered
  }, [items, liveOnly, sort])

  const sortButton = (key: SortKey, label: string) => (
    <button
      key={key}
      type="button"
      onClick={() => setSort(key)}
      aria-pressed={sort === key}
      className={cn(
        'h-9 rounded-lg px-3 text-sm font-medium transition-colors duration-200',
        sort === key
          ? 'bg-primary-500/15 text-primary-300'
          : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
      )}
    >
      {label}
    </button>
  )

  return (
    <div className="mx-auto w-full max-w-[1600px] space-y-6">
      <SectionHeader
        title="Catégories"
        subtitle="Jeux, musique, talk-shows : trouve ton univers"
        accent
      />

      {/* Barre d'outils : recherche + tri + filtre live */}
      <div className="flex flex-wrap items-center gap-2">
        <div className="relative min-w-52 flex-1">
          <Icon
            name="search"
            className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
          />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher une catégorie…"
            aria-label="Rechercher une catégorie"
            className="pl-9"
          />
        </div>
        <div
          className="flex items-center gap-1 rounded-lg border border-border-default bg-bg-surface p-1"
          role="group"
          aria-label="Trier les catégories"
        >
          {sortButton('popularity', 'Popularité')}
          {sortButton('name', 'A-Z')}
        </div>
        <button
          type="button"
          onClick={() => setLiveOnly((value) => !value)}
          aria-pressed={liveOnly}
          className={cn(
            'flex h-9 items-center gap-1.5 rounded-lg border px-3 text-sm font-medium transition-colors duration-200',
            liveOnly
              ? 'border-primary-500/60 bg-primary-500/15 text-primary-300'
              : 'border-border-default bg-bg-surface text-text-secondary hover:text-text-primary',
          )}
        >
          <span className={cn('h-2 w-2 rounded-full', liveOnly ? 'animate-pulse-live bg-live' : 'bg-text-muted')} />
          En direct
        </button>
      </div>

      {/* Contenu */}
      {status === 'loading' && (
        <>
          {/* Mobile : tuiles maquette */}
          <div
            className="grid grid-cols-3 gap-2 md:hidden"
            aria-busy="true"
            aria-label="Chargement des catégories"
          >
            {Array.from({ length: 9 }).map((_, index) => (
              <div
                key={index}
                aria-hidden="true"
                className="flex min-h-[104px] animate-pulse flex-col items-center justify-center gap-2 rounded-2xl border border-border-default bg-bg-surface"
              >
                <div className="h-10 w-10 rounded-full bg-bg-elevated" />
                <div className="h-3 w-2/3 rounded bg-bg-elevated" />
              </div>
            ))}
          </div>
          <div
            className="hidden grid-cols-2 gap-x-3 gap-y-6 sm:grid-cols-3 md:grid lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6"
            aria-busy="true"
            aria-label="Chargement des catégories"
          >
            {Array.from({ length: 12 }).map((_, index) => (
              <CategoryCardSkeleton key={index} />
            ))}
          </div>
        </>
      )}

      {status === 'error' && (
        <EmptyState
          icon="grid"
          title="Catégories indisponibles"
          description="Impossible de charger les catégories pour le moment. Vérifie ta connexion puis réessaie."
          action={
            <button
              type="button"
              onClick={() => void load(query)}
              className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
            >
              Réessayer
            </button>
          }
        />
      )}

      {status === 'ready' && visible.length === 0 && (
        <EmptyState
          icon="search"
          title={liveOnly ? 'Aucune catégorie en direct' : 'Aucune catégorie trouvée'}
          description={
            liveOnly
              ? 'Aucun live n\'est en cours dans ces catégories. Désactive le filtre pour tout voir.'
              : `Aucune catégorie ne correspond à « ${query} ». Essaie un autre mot-clé.`
          }
          action={
            liveOnly ? (
              <button
                type="button"
                onClick={() => setLiveOnly(false)}
                className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
              >
                Tout afficher
              </button>
            ) : undefined
          }
        />
      )}

      {status === 'ready' && visible.length > 0 && (
        <>
          {/* Mobile : tuiles maquette 3 colonnes */}
          <div className="grid grid-cols-3 gap-2 md:hidden">
            {visible.map((category) => (
              <MobileCategoryTile key={category.id} category={category} />
            ))}
          </div>
          <div className="hidden grid-cols-2 gap-x-3 gap-y-6 sm:grid-cols-3 md:grid lg:grid-cols-4 xl:grid-cols-5 2xl:grid-cols-6">
            {visible.map((category) => (
              <CategoryCard key={category.id} category={category} />
            ))}
          </div>
        </>
      )}
    </div>
  )
}
