import { Link } from 'react-router'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDuration } from '../../utils/format'
import { Badge } from '../ui'
import { cn } from '../../utils/cn'
import type { Clip, Video } from '../../services/api/videos'

/// Carte VOD (étape 15) : vignette 16/9 + durée, titre, chaîne, vues, date.
/// Réutilise les motifs `LiveCard` (lien, overlay, line-clamp).
export function VideoCard({ video, channelName }: { video: Video; channelName?: string }) {
  const poster = video.thumbnailUrl !== null ? (mediaUrl(video.thumbnailUrl) ?? undefined) : undefined
  return (
    <Link
      to={`/video/${video.id}`}
      className="group overflow-hidden rounded-xl border border-border-default bg-bg-surface transition-all hover:border-border-hover hover:shadow-lg"
    >
      <div className="relative aspect-video w-full overflow-hidden bg-bg-elevated">
        {poster !== undefined ? (
          <img
            src={poster}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-4xl" aria-hidden="true">
            🎬
          </div>
        )}
        <span className="absolute bottom-2 right-2 rounded bg-black/70 px-1.5 py-0.5 text-xs font-medium text-white">
          {formatDuration(video.durationSeconds)}
        </span>
        {video.processingStatus !== 'READY' && (
          <span className="absolute left-2 top-2">
            <Badge variant="warning">Traitement</Badge>
          </span>
        )}
      </div>
      <div className="p-3">
        <p className="line-clamp-2 text-sm font-semibold text-text-primary group-hover:underline">
          {video.title}
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          {channelName ?? `Chaîne #${video.creatorId}`} · {formatCount(video.viewCount)} vues
        </p>
      </div>
    </Link>
  )
}

/// Carte clip (étape 15) : verticale compacte, durée courte, auteur du découpage.
export function ClipCard({ clip, className }: { clip: Clip; className?: string }) {
  const poster = clip.thumbnailUrl !== null ? (mediaUrl(clip.thumbnailUrl) ?? undefined) : undefined
  return (
    <Link
      to={`/clip/${clip.id}`}
      className={cn(
        'group overflow-hidden rounded-xl border border-border-default bg-bg-surface transition-all hover:border-border-hover hover:shadow-lg',
        className,
      )}
    >
      <div className="relative aspect-video w-full overflow-hidden bg-bg-elevated">
        {poster !== undefined ? (
          <img
            src={poster}
            alt=""
            loading="lazy"
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="flex h-full w-full items-center justify-center text-4xl" aria-hidden="true">
            ✂️
          </div>
        )}
        <span className="absolute bottom-2 right-2 rounded bg-black/70 px-1.5 py-0.5 text-xs font-medium text-white">
          {formatDuration(clip.durationSeconds)}
        </span>
        <span className="absolute left-2 top-2">
          <Badge variant="accent">Clip</Badge>
        </span>
      </div>
      <div className="p-3">
        <p className="line-clamp-2 text-sm font-semibold text-text-primary group-hover:underline">
          {clip.title}
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          {formatCount(clip.viewCount)} vues · {formatDuration(clip.startsAtSeconds)} du direct
        </p>
      </div>
    </Link>
  )
}
