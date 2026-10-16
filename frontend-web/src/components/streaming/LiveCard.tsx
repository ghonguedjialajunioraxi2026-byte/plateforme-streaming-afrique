import { Link } from 'react-router'
import type { Stream } from '../../types/domain'
import { LiveBadge } from './LiveBadge'
import { ViewerCount } from './ViewerCount'

interface LiveCardProps {
  stream: Stream
  /** Nom affiché de la chaîne (résolu via channelId) */
  channelName?: string
  /** Badges de langue/format affichés sous le titre */
  tags?: string[]
  /** Affiche la coche de chaîne vérifiée */
  verified?: boolean
}

/**
 * Carte de live, signature visuelle Twitch adaptée à Flick :
 * au survol, la vignette glisse de 8px vers le haut-gauche et révèle
 * un fond vert Flick (transition 200ms ease-out). Titre vert au hover.
 * Sur tactile : aucun hover, la carte reste lisible sans l'effet.
 */
export function LiveCard({ stream, channelName, tags = [], verified = false }: LiveCardProps) {
  return (
    <Link to={`/live/${stream.id}`} className="group block min-w-0 rounded-lg transition-opacity duration-150 active:opacity-80">
      {/* Vignette + fond vert révélé au hover */}
      <div className="relative">
        <div
          aria-hidden="true"
          className="absolute inset-0 rounded-lg bg-primary-500 transition-transform duration-200 ease-out group-hover:translate-x-2 group-hover:translate-y-2"
        />
        <div className="relative aspect-video overflow-hidden rounded-lg border border-border-default bg-bg-elevated transition-transform duration-200 ease-out group-hover:-translate-x-1 group-hover:-translate-y-1">
          {stream.thumbnailUrl ? (
            <img
              src={stream.thumbnailUrl}
              alt={stream.title}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-300 ease-out group-hover:scale-105"
            />
          ) : (
            <div
              aria-hidden="true"
              className="flex h-full w-full items-center justify-center bg-[radial-gradient(ellipse_at_center,var(--color-bg-hover)_0%,var(--color-bg-secondary)_75%)] text-4xl"
            >
              🎬
            </div>
          )}

          {/* Badge LIVE + spectateurs en surimpression */}
          <div className="absolute left-2 top-2">
            <LiveBadge />
          </div>
          <div className="absolute bottom-2 left-2 rounded-md bg-black/70 px-2 py-0.5">
            <ViewerCount viewers={stream.viewersCount} />
          </div>
        </div>
      </div>

      {/* Infos */}
      <div className="flex gap-2.5 px-0.5 pt-2.5">
        <span
          aria-hidden="true"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-bg-elevated text-sm font-bold text-primary-400 transition-colors duration-200 group-hover:bg-primary-500/15"
        >
          {(channelName ?? stream.channelId).charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0 flex-1">
          <h3 className="line-clamp-1 font-semibold text-text-primary transition-colors duration-200 group-hover:text-primary-300">
            {stream.title}
          </h3>
          <p className="mt-0.5 line-clamp-1 text-sm text-text-secondary">
            {channelName ?? stream.channelId}{' '}
            {verified && (
              <span aria-label="Chaîne vérifiée" className="text-primary-400">
                ✓
              </span>
            )}
          </p>
          <p className="line-clamp-1 text-xs text-text-muted">{stream.category}</p>
          {tags.length > 0 && (
            <div className="mt-1.5 flex flex-wrap gap-1">
              {tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full bg-bg-elevated px-2 py-0.5 text-[11px] font-medium text-text-secondary transition-colors duration-200 hover:bg-bg-hover hover:text-text-primary"
                >
                  {tag}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>
    </Link>
  )
}
