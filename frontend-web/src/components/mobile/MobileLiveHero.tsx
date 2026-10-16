import { Link } from 'react-router'
import type { HeroSlide } from '../../constants/discover'
import { channelNameFor } from '../../constants/discover'
import { formatCount } from '../../utils/format'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'

/**
 * Hero mobile de l'accueil (maquette écran 4) : grand visuel 16/9 aux
 * coins arrondis, badges LIVE rouge + audience en haut, dégradé bas
 * avec avatar, nom vérifié, titre et catégorie. Un seul live vedette
 * (pas de carousel sur mobile : swipe réservé aux rails).
 */
export function MobileLiveHero({ slide, className }: { slide: HeroSlide; className?: string }) {
  const name = channelNameFor(slide.channelId)

  return (
    <Link
      to={`/live/${slide.id}`}
      className={cn('block min-w-0 active:opacity-90', className)}
      aria-label={`Regarder le live vedette de ${name} : ${slide.title}`}
    >
      <div className="relative aspect-video overflow-hidden rounded-2xl border border-border-default bg-bg-elevated">
        <div
          aria-hidden="true"
          className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-accent-500)_0%,var(--color-bg-hover)_50%,var(--color-bg-secondary)_100%)] text-6xl"
        >
          🎮
        </div>
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/85 via-black/20 to-black/10"
        />
        {/* Badges haut */}
        <div className="absolute inset-x-2.5 top-2.5 flex items-center justify-between">
          <span className="flex items-center gap-1 rounded-md bg-danger px-2 py-1 text-[11px] font-bold tracking-wide text-white uppercase">
            <span aria-hidden="true" className="h-1.5 w-1.5 animate-pulse-live rounded-full bg-white" />
            Live
          </span>
          <span className="flex items-center gap-1 rounded-md bg-black/70 px-2 py-1 text-[11px] font-semibold text-white">
            <Icon name="eye" className="h-3.5 w-3.5" />
            {formatCount(slide.viewersCount)}
          </span>
        </div>
        {/* Infos bas */}
        <div className="absolute inset-x-0 bottom-0 flex items-end gap-2.5 p-3">
          <span
            aria-hidden="true"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-bg-elevated text-base font-bold text-primary-400 ring-2 ring-white/20"
          >
            {name.charAt(0).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <p className="line-clamp-1 text-sm font-bold text-white">
              {name}{' '}
              {slide.verified && (
                <span aria-label="Chaîne vérifiée" className="text-xs text-primary-400">
                  ✓
                </span>
              )}
            </p>
            <p className="line-clamp-1 text-[13px] font-medium text-white/90">{slide.title}</p>
            <p className="mt-0.5 line-clamp-1 text-xs text-white/70">
              🎮 {slide.category}
            </p>
          </div>
        </div>
      </div>
    </Link>
  )
}
