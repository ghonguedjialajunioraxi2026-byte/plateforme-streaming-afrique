import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { Play, Heart, CaretLeft, SealCheck, MapPin, Eye } from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import type { FlickFeatured } from '../../types/flick'
import { formatViewers } from '../../data/flick'
import { FlickAvatar } from './FlickAvatar'
import { FlickLiveBadge } from './FlickLiveBadge'

interface FlickHeroProps {
  slides: FlickFeatured[]
  autoPlayMs?: number
}

/**
 * Carrousel "Featured Live" — hero cinématique 16:9, min-h 360px,
 * gradient sombre, badge LIVE, viewers, créateur vérifié + pays,
 * titre, tags, CTA Regarder + Favoris.
 */
export function FlickHero({ slides, autoPlayMs = 8000 }: FlickHeroProps) {
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)
  const [favorite, setFavorite] = useState(false)
  const timer = useRef<number | null>(null)
  const count = slides.length

  const goTo = useCallback((next: number) => {
    setIndex(((next % count) + count) % count)
  }, [count])

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
      className="relative min-h-[360px] overflow-hidden rounded-2xl border border-border-default bg-bg-surface"
    >
      <div className="grid min-h-[360px] lg:grid-cols-2" aria-live="polite">
        {/* Visuel cinématique */}
        <Link
          to={`/live/${slide.id}`}
          className="group relative block min-h-[240px] overflow-hidden lg:min-h-[360px]"
          aria-label={`Regarder ${slide.streamer.name} : ${slide.title}`}
        >
          <div
            aria-hidden="true"
            className="absolute inset-0 flex items-center justify-center text-8xl"
            style={{ background: `radial-gradient(ellipse at 40% 50%, hsl(${slide.streamer.avatarHue} 70% 18%) 0%, #05070A 70%)` }}
          >
            {slide.artwork}
          </div>
          <div className="absolute inset-0 bg-gradient-to-t from-[#05070A] via-[#05070A]/30 to-transparent" />
          <div className="absolute inset-0 hidden bg-gradient-to-r from-transparent via-transparent to-bg-surface lg:block" />
          <div className="absolute left-4 top-4 flex items-center gap-2">
            <FlickLiveBadge />
            <span className="flex items-center gap-1.5 rounded-md bg-black/70 px-2.5 py-1 text-xs font-semibold text-white backdrop-blur">
              <Eye size={14} />
              {formatViewers(slide.viewers)} spectateurs
            </span>
          </div>
          <div className="absolute inset-0 flex items-center justify-center opacity-0 transition-opacity duration-300 group-hover:opacity-100">
            <span className="flex h-20 w-20 items-center justify-center rounded-full bg-primary-500 text-black shadow-glow-primary transition-transform duration-300 group-hover:scale-110">
              <Play size={34} weight="fill" />
            </span>
          </div>
          <span className="absolute bottom-4 left-4 rounded-md bg-black/70 px-2 py-1 text-xs text-[#94A3B8] backdrop-blur">
            {slide.startedAt} · {slide.language}
          </span>
        </Link>

        {/* Infos créateur */}
        <div className="relative flex flex-col justify-center gap-3 p-6 lg:p-8">
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-primary-500/5 via-transparent to-transparent" />
          <div className="relative flex items-center gap-3">
            <FlickAvatar name={slide.streamer.name} hue={slide.streamer.avatarHue} size="lg" />
            <div className="min-w-0">
              <p className="flex items-center gap-1.5 font-bold text-text-primary">
                <span className="truncate">{slide.streamer.name}</span>
                {slide.streamer.verified && <SealCheck size={17} weight="fill" className="shrink-0 text-primary-400" aria-label="Chaîne vérifiée" />}
              </p>
              <p className="mt-0.5 flex items-center gap-1 text-sm text-text-secondary">
                <MapPin size={13} />
                {slide.streamer.country}
                <span aria-hidden="true">·</span>
                {slide.category}
              </p>
            </div>
          </div>
          <h2 className="relative font-display text-2xl font-black leading-tight tracking-tight text-text-primary xl:text-[28px]">
            {slide.title}
          </h2>
          <p className="relative line-clamp-2 text-sm leading-relaxed text-text-secondary">{slide.description}</p>
          <div className="relative flex flex-wrap gap-1.5">
            {slide.tags.map((tag) => (
              <span key={tag} className="rounded-full border border-border-default bg-bg-elevated px-2.5 py-1 text-xs font-medium text-text-secondary">
                #{tag}
              </span>
            ))}
          </div>
          <div className="relative mt-1 flex items-center gap-2.5">
            <Link
              to={`/live/${slide.id}`}
              className="flex h-11 items-center gap-2 rounded-xl bg-primary-500 px-5 text-sm font-bold text-black shadow-glow-primary transition-all hover:bg-primary-300 hover:shadow-glow-primary"
            >
              <Play size={17} weight="fill" />
              Regarder le live
            </Link>
            <button
              type="button"
              onClick={() => setFavorite((v) => !v)}
              aria-pressed={favorite}
              aria-label={favorite ? 'Retirer des favoris' : 'Ajouter aux favoris'}
              className={cn(
                'flex h-11 w-11 items-center justify-center rounded-xl border transition-colors',
                favorite
                  ? 'border-primary-500/60 bg-primary-500/10 text-primary-400'
                  : 'border-border-default bg-bg-elevated text-text-secondary hover:text-text-primary',
              )}
            >
              <Heart size={20} weight={favorite ? 'fill' : 'regular'} />
            </button>
          </div>
        </div>
      </div>

      {/* Contrôles */}
      {count > 1 && (
        <>
          <div className="absolute left-3 top-1/2 hidden -translate-y-1/2 lg:block">
            <button type="button" onClick={() => goTo(index - 1)} aria-label="Slide précédente" className="flex h-9 w-9 items-center justify-center rounded-full bg-black/60 text-white backdrop-blur transition-colors hover:bg-primary-500 hover:text-black">
              <CaretLeft size={18} weight="bold" />
            </button>
          </div>
          <div className="absolute bottom-4 right-5 flex items-center gap-1.5" role="tablist" aria-label="Choisir le live vedette">
            {slides.map((s, i) => (
              <button
                key={s.id}
                type="button"
                role="tab"
                aria-selected={i === index}
                aria-label={`Voir ${s.streamer.name}`}
                onClick={() => goTo(i)}
                className={cn('h-1.5 rounded-full transition-all', i === index ? 'w-7 bg-primary-500 shadow-glow-primary' : 'w-1.5 bg-border-hover hover:bg-text-secondary')}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}
