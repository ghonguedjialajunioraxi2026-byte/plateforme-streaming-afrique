import { Link } from 'react-router'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDuration } from '../../utils/format'
import { cn } from '../../utils/cn'

interface MobileVideoRowProps {
  id: string
  kind: 'video' | 'clip' | 'live'
  title: string
  channelName: string
  views: number
  thumbnailUrl?: string | null
  durationSeconds?: number
  badge?: string
  className?: string
}

/**
 * Ligne vidéo mobile (maquette écrans Recherche/Vidéos) : vignette
 * 16/9 à gauche (durée ou LIVE), titre + chaîne · vues à droite.
 * Compacte, tactile, sans bordure — pensée pour les listes verticales.
 */
export function MobileVideoRow({
  id,
  kind,
  title,
  channelName,
  views,
  thumbnailUrl,
  durationSeconds,
  badge,
  className,
}: MobileVideoRowProps) {
  const to = kind === 'video' ? `/video/${id}` : kind === 'clip' ? `/clip/${id}` : `/live/${id}`
  const poster = thumbnailUrl != null ? (mediaUrl(thumbnailUrl) ?? undefined) : undefined

  return (
    <Link
      to={to}
      className={cn('flex min-h-11 gap-3 py-1 active:opacity-80', className)}
      aria-label={`${kind === 'live' ? 'Regarder en direct' : 'Regarder'} : ${title}`}
    >
      <div className="relative aspect-video w-36 shrink-0 overflow-hidden rounded-lg border border-border-default bg-bg-elevated">
        {poster !== undefined ? (
          <img src={poster} alt="" loading="lazy" draggable={false} className="h-full w-full object-cover" />
        ) : (
          <div
            aria-hidden="true"
            className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-bg-hover)_0%,var(--color-bg-secondary)_80%)] text-2xl"
          >
            {kind === 'clip' ? '✂️' : '🎬'}
          </div>
        )}
        {kind === 'live' ? (
          <span className="absolute bottom-1 left-1 flex items-center gap-1 rounded bg-danger px-1.5 py-0.5 text-[10px] font-bold text-white uppercase">
            <span aria-hidden="true" className="h-1.5 w-1.5 animate-pulse-live rounded-full bg-white" />
            Live
          </span>
        ) : (
          durationSeconds !== undefined && (
            <span className="absolute right-1 bottom-1 rounded bg-black/70 px-1.5 py-0.5 text-[11px] font-medium text-white">
              {formatDuration(durationSeconds)}
            </span>
          )
        )}
        {badge !== undefined && kind !== 'live' && (
          <span className="absolute top-1 left-1 rounded bg-accent-500 px-1.5 py-0.5 text-[10px] font-bold text-white uppercase">
            {badge}
          </span>
        )}
      </div>
      <div className="min-w-0 flex-1 py-0.5">
        <p className="line-clamp-2 text-sm leading-snug font-semibold text-text-primary">{title}</p>
        <p className="mt-1 line-clamp-1 text-xs text-text-secondary">
          {channelName} · {formatCount(views)} vues
        </p>
      </div>
    </Link>
  )
}
