import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'
import { Avatar, Button } from '../ui'
import { Icon } from '../ui/icons'
import { LiveBadge } from './LiveBadge'
import { ViewerCount } from './ViewerCount'
import { channelNameFor, type HeroSlide } from '../../constants/discover'
import { formatCount } from '../../utils/format'

interface HeroCarouselProps {
  slides: HeroSlide[]
  /** Rotation automatique (ms, 0 = désactivée) */
  autoPlayMs?: number
  className?: string
}

const FAVORITE_KEY = 'flick-hero-favorite'

function HeroFavoriteButton() {
  const [favorite, setFavorite] = useState<boolean>(() => {
    try {
      return window.localStorage.getItem(FAVORITE_KEY) === '1'
    } catch {
      return false
    }
  })

  function toggle() {
    setFavorite((previous) => {
      try {
        window.localStorage.setItem(FAVORITE_KEY, previous ? '0' : '1')
      } catch {
        // Stockage indisponible : le bouton reste fonctionnel en local.
      }
      return !previous
    })
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-pressed={favorite}
      aria-label={favorite ? 'Retirer des favoris' : 'Ajouter aux favoris'}
      title={favorite ? 'Retirer des favoris' : 'Ajouter aux favoris'}
      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-border-default bg-bg-elevated text-text-secondary transition-colors hover:border-border-hover hover:text-text-primary"
    >
      <Icon name="heart" className={favorite ? 'h-5 w-5 text-primary-400' : 'h-5 w-5'} />
    </button>
  )
}

/**
 * Carousel du live mis en avant (homepage).
 * Flèches + indicateurs (vert FLICK pour l'état actif), rotation auto
 * en pause au survol/focus. Réutilise LiveBadge, ViewerCount, Avatar.
 */
export function HeroCarousel({ slides, autoPlayMs = 8000, className }: HeroCarouselProps) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const timer = useRef<number | null>(null)
  const count = slides.length

  const goTo = useCallback(
    (next: number) => {
      setIndex(((next % count) + count) % count)
    },
    [count],
  )

  useEffect(() => {
    if (autoPlayMs <= 0 || paused || count < 2) return
    timer.current = window.setTimeout(() => goTo(index + 1), autoPlayMs)
    return () => {
      if (timer.current !== null) window.clearTimeout(timer.current)
    }
  }, [index, paused, autoPlayMs, count, goTo])

  if (count === 0) return null
  const slide = slides[index]

  return (
    <section
      aria-roledescription="carousel"
      aria-label="Lives mis en avant"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      className={cn(
        'relative overflow-hidden rounded-lg border border-border-default bg-bg-surface',
        className,
      )}
    >
      <div className="grid gap-0 lg:grid-cols-5" aria-live="polite">
        {/* Visuel principal */}
        <Link
          to={`/live/${slide.id}`}
          className="group relative block aspect-video bg-bg-elevated lg:col-span-3"
          aria-label={`Regarder le live de ${channelNameFor(slide.channelId)} : ${slide.title}`}
        >
          <div
            aria-hidden="true"
            className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-bg-elevated)_0%,var(--color-bg-primary)_75%)] text-7xl"
          >
            🎮
          </div>
          <div className="absolute inset-0 bg-gradient-to-t from-black/40 to-transparent" />
          <div className="absolute left-3 top-3 flex items-center gap-2">
            <LiveBadge size="md" />
          </div>
          <div className="absolute bottom-3 left-3 rounded-md bg-black/70 px-2 py-1">
            <ViewerCount viewers={slide.viewersCount} size="md" />
          </div>
          <div className="absolute inset-0 flex items-center justify-center opacity-0 transition-opacity group-hover:opacity-100">
            <span className="flex h-16 w-16 items-center justify-center rounded-full bg-primary-500 text-text-inverse shadow-glow-primary">
              <Icon name="play" className="h-7 w-7" />
            </span>
          </div>
        </Link>

        {/* Infos du live */}
        <div className="flex flex-col justify-center gap-2.5 p-4 lg:col-span-2 lg:p-6">
          <div className="flex items-center gap-3">
            <Avatar name={channelNameFor(slide.channelId)} size="md" />
            <div className="min-w-0">
              <p className="flex items-center gap-1.5 font-semibold text-text-primary">
                {channelNameFor(slide.channelId)}
                {slide.verified && (
                  <span aria-label="Chaîne vérifiée" className="text-sm text-primary-400">
                    ✓
                  </span>
                )}
              </p>
              <p className="truncate text-sm text-text-muted">
                {slide.category} · {formatCount(slide.channelFollowers)} abonnés
              </p>
            </div>
          </div>
          <h2 className="font-display text-lg font-bold leading-snug text-text-primary lg:text-xl">
            {slide.title}
          </h2>
          <p className="text-sm text-text-secondary">{slide.description}</p>
          <div className="flex flex-wrap gap-1.5">
            {slide.tags.map((tag) => (
              <span
                key={tag}
                className="rounded bg-bg-elevated px-2 py-0.5 text-xs font-medium text-text-secondary"
              >
                {tag}
              </span>
            ))}
          </div>
          <div className="mt-1 flex items-center gap-2">
            <Link to={`/live/${slide.id}`}>
              <Button>Regarder le live</Button>
            </Link>
            <HeroFavoriteButton />
          </div>
        </div>
      </div>

      {/* Contrôles carousel */}
      {count > 1 && (
        <>
          <div className="absolute left-2 top-1/2 hidden -translate-y-1/2 gap-2 lg:flex">
            <button
              type="button"
              onClick={() => goTo(index - 1)}
              aria-label="Slide précédente"
              className="flex h-8 w-8 items-center justify-center rounded-full bg-black/60 text-text-primary transition-colors hover:bg-primary-500 hover:text-text-inverse"
            >
              <Icon name="chevron-down" className="h-4 w-4 rotate-90" />
            </button>
          </div>
          <div className="absolute right-2 top-1/2 hidden -translate-y-1/2 gap-2 lg:flex">
            <button
              type="button"
              onClick={() => goTo(index + 1)}
              aria-label="Slide suivante"
              className="flex h-8 w-8 items-center justify-center rounded-full bg-black/60 text-text-primary transition-colors hover:bg-primary-500 hover:text-text-inverse"
            >
              <Icon name="chevron-down" className="h-4 w-4 -rotate-90" />
            </button>
          </div>
          <div className="absolute bottom-3 right-3 flex items-center gap-1.5" role="tablist" aria-label="Choisir le live mis en avant">
            {slides.map((candidate, candidateIndex) => (
              <button
                key={candidate.id}
                type="button"
                role="tab"
                aria-selected={candidateIndex === index}
                aria-label={`Voir ${channelNameFor(candidate.channelId)}`}
                onClick={() => goTo(candidateIndex)}
                className={cn(
                  'h-1.5 rounded-full transition-all',
                  candidateIndex === index
                    ? 'w-6 bg-primary-500'
                    : 'w-1.5 bg-text-muted hover:bg-text-secondary',
                )}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}
