import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'
import { Avatar } from '../ui'
import { creatorsApi } from '../../services/api/creators'
import { categoriesApi } from '../../services/api/categories'
import type { ChannelDetail } from '../../types/domain'

interface CategorySuggestion {
  id: number
  name: string
}

/**
 * Barre de recherche façon Twitch : suggestions temps réel groupées
 * (chaînes + catégories), debounce 300ms, réponses obsolètes ignorées
 * (compteur de version — le client HTTP partagé n'expose pas d'abort),
 * navigation clavier (Échap ferme), fermeture au clic extérieur.
 */
export function SearchBar() {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [channels, setChannels] = useState<ChannelDetail[]>([])
  const [categories, setCategories] = useState<CategorySuggestion[]>([])
  const navigate = useNavigate()
  const rootRef = useRef<HTMLDivElement>(null)
  const versionRef = useRef(0)

  // Fermeture au clic extérieur.
  useEffect(() => {
    function handlePointerDown(event: PointerEvent) {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', handlePointerDown)
    return () => document.removeEventListener('pointerdown', handlePointerDown)
  }, [])

  // Suggestions debouncées (2 caractères min).
  useEffect(() => {
    const text = query.trim()
    if (text.length < 2) {
      setChannels([])
      setCategories([])
      setLoading(false)
      return
    }
    setLoading(true)
    const version = versionRef.current + 1
    versionRef.current = version
    const timer = window.setTimeout(() => {
      void Promise.all([
        creatorsApi.list(text, 0, 4).catch(() => null),
        categoriesApi.list(text, 0, 4).catch(() => null),
      ]).then(([channelPage, categoryPage]) => {
        if (versionRef.current !== version) return // frappe obsolète : on ignore.
        setChannels(channelPage?.content ?? [])
        setCategories(
          (categoryPage?.content ?? []).map((category) => ({
            id: category.id,
            name: category.name,
          })),
        )
        setLoading(false)
        setOpen(true)
      })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [query])

  function submit(event?: FormEvent) {
    event?.preventDefault()
    const text = query.trim()
    setOpen(false)
    navigate(text ? `/recherche?q=${encodeURIComponent(text)}` : '/recherche')
  }

  const hasResults = channels.length > 0 || categories.length > 0
  const showPanel = open && query.trim().length >= 2

  return (
    <div ref={rootRef} className="relative mx-auto w-full min-w-[280px] max-w-[520px] flex-1">
      <form onSubmit={submit} role="search">
        <Icon
          name="search"
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
        />
        <input
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onFocus={() => {
            if (query.trim().length >= 2) setOpen(true)
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setOpen(false)
          }}
          placeholder="Rechercher un créateur, un jeu, une catégorie..."
          aria-label="Rechercher un créateur, un jeu, une catégorie"
          aria-expanded={showPanel}
          aria-controls="search-suggestions"
          role="combobox"
          aria-autocomplete="list"
          className="h-9 w-full rounded-lg border border-border-default bg-bg-surface pl-9 pr-8 text-sm text-text-primary placeholder:text-text-muted transition-colors duration-200 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/30"
        />
        {query && (
          <button
            type="button"
            onClick={() => {
              setQuery('')
              setOpen(false)
            }}
            aria-label="Effacer la recherche"
            className="absolute right-2 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded text-text-muted transition-colors hover:text-text-primary"
          >
            <Icon name="x" className="h-3.5 w-3.5" />
          </button>
        )}
      </form>

      {showPanel && (
        <div
          id="search-suggestions"
          role="listbox"
          aria-label="Suggestions de recherche"
          className="animate-fade-in absolute inset-x-0 top-11 z-40 overflow-hidden rounded-xl border border-border-default bg-bg-secondary shadow-xl"
        >
          {loading && !hasResults ? (
            <p className="px-4 py-3 text-sm text-text-muted">Recherche en cours…</p>
          ) : !hasResults ? (
            <button
              type="button"
              onClick={() => submit()}
              className="block w-full px-4 py-3 text-left text-sm text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              Rechercher « {query.trim()} »
            </button>
          ) : (
            <div className="py-1.5">
              {channels.length > 0 && (
                <div role="group" aria-label="Chaînes suggérées">
                  <p className="px-4 pb-1 pt-1.5 text-[11px] font-bold tracking-wider text-text-muted uppercase">
                    Chaînes
                  </p>
                  {channels.map((channel) => (
                    <Link
                      key={channel.id}
                      to={`/channel/${channel.slug}`}
                      onClick={() => setOpen(false)}
                      role="option"
                      aria-selected="false"
                      className="flex items-center gap-2.5 px-4 py-2 text-sm transition-colors duration-150 hover:bg-bg-hover"
                    >
                      <Avatar name={channel.displayName} size="sm" />
                      <span className="min-w-0 flex-1 truncate font-medium text-text-primary">
                        {channel.displayName}
                      </span>
                      <span className="shrink-0 text-xs text-text-muted">
                        {channel.followerCount.toLocaleString('fr')} abonnés
                      </span>
                    </Link>
                  ))}
                </div>
              )}
              {categories.length > 0 && (
                <div role="group" aria-label="Catégories suggérées">
                  <p className="px-4 pb-1 pt-1.5 text-[11px] font-bold tracking-wider text-text-muted uppercase">
                    Catégories
                  </p>
                  {categories.map((category) => (
                    <Link
                      key={category.id}
                      to={`/recherche?q=${encodeURIComponent(category.name)}`}
                      onClick={() => setOpen(false)}
                      role="option"
                      aria-selected="false"
                      className="flex items-center gap-2.5 px-4 py-2 text-sm transition-colors duration-150 hover:bg-bg-hover"
                    >
                      <Icon name="grid" className="h-4 w-4 shrink-0 text-text-muted" />
                      <span className="truncate font-medium text-text-primary">
                        {category.name}
                      </span>
                    </Link>
                  ))}
                </div>
              )}
              <button
                type="button"
                onClick={() => submit()}
                className={cn(
                  'mt-1.5 flex w-full items-center gap-2 border-t border-border-default px-4 py-2.5 text-sm font-medium text-primary-400 transition-colors duration-150 hover:bg-bg-hover hover:text-primary-300',
                )}
              >
                <Icon name="search" className="h-4 w-4" />
                Tous les résultats pour « {query.trim()} »
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
