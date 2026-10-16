import { Link } from 'react-router'
import type { Stream } from '../../types/domain'
import { formatCount } from '../../utils/format'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'

interface MobileLiveCardProps {
  stream: Stream
  channelName?: string
  verified?: boolean
  /** Lien de destination (défaut : `/live/:id`) */
  to?: string
  /**
   * Variante `compact` (grille maquette 3 colonnes : vignette + nom +
   * catégorie) vs `full` (avatar + titre + catégorie + tags).
   */
  variant?: 'compact' | 'full'
  className?: string
}

/**
 * Carte de live mobile fidèle à la maquette :
 * vignette arrondie, badge LIVE rouge en haut à gauche (comme sur la
 * maquette — le desktop garde son badge vert), pastille d'audience
 * en bas à gauche, nom + coche vérifiée + catégorie en dessous.
 * Feedback tactile subtil (opacité), pas d'effet hover.
 */
export function MobileLiveCard({
  stream,
  channelName,
  verified = false,
  to,
  variant = 'full',
  className,
}: MobileLiveCardProps) {
  const name = channelName ?? String(stream.channelId)

  return (
    <Link
      to={to ?? `/live/${stream.id}`}
      className={cn('group block min-w-0 active:opacity-80', className)}
      aria-label={`Regarder ${stream.title} par ${name} en direct`}
    >
      <div className="relative aspect-video overflow-hidden rounded-xl border border-border-default bg-bg-elevated">
        {stream.thumbnailUrl ? (
          <img
            src={stream.thumbnailUrl}
            alt=""
            loading="lazy"
            draggable={false}
            className="h-full w-full object-cover"
          />
        ) : (
          <div
            aria-hidden="true"
            className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-accent-500)_0%,var(--color-bg-hover)_45%,var(--color-bg-secondary)_100%)] text-3xl"
          >
            🎬
          </div>
        )}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/35 via-transparent to-black/10"
        />
        {/* Badge LIVE rouge maquette */}
        <span className="absolute left-1.5 top-1.5 flex items-center gap-1 rounded-md bg-danger px-1.5 py-0.5 text-[10px] font-bold tracking-wide text-white uppercase">
          <span aria-hidden="true" className="h-1.5 w-1.5 animate-pulse-live rounded-full bg-white" />
          Live
        </span>
        {/* Audience */}
        <span className="absolute bottom-1.5 left-1.5 flex items-center gap-1 rounded-md bg-black/70 px-1.5 py-0.5 text-[11px] font-semibold text-white">
          <Icon name="eye" className="h-3 w-3" />
          {formatCount(stream.viewersCount)}
        </span>
      </div>

      {variant === 'compact' ? (
        <div className="min-w-0 px-0.5 pt-1.5">
          <p className="line-clamp-1 text-[13px] font-semibold text-text-primary">
            {name}{' '}
            {verified && (
              <span aria-label="Chaîne vérifiée" className="text-[11px] text-primary-400">
                ✓
              </span>
            )}
          </p>
          <p className="line-clamp-1 text-xs text-text-muted">{stream.category}</p>
        </div>
      ) : (
        <div className="flex gap-2 px-0.5 pt-2">
          <span
            aria-hidden="true"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-bg-elevated text-sm font-bold text-primary-400"
          >
            {name.charAt(0).toUpperCase()}
          </span>
          <div className="min-w-0 flex-1">
            <p className="line-clamp-1 text-sm font-semibold text-text-primary">{stream.title}</p>
            <p className="mt-0.5 line-clamp-1 text-[13px] text-text-secondary">
              {name}{' '}
              {verified && (
                <span aria-label="Chaîne vérifiée" className="text-xs text-primary-400">
                  ✓
                </span>
              )}
            </p>
            <p className="line-clamp-1 text-xs text-text-muted">{stream.category}</p>
          </div>
        </div>
      )}
    </Link>
  )
}
