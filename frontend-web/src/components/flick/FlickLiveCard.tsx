import { Link } from 'react-router'
import { SealCheck, MapPin } from '@phosphor-icons/react'
import type { FlickLive } from '../../types/flick'
import { formatViewers } from '../../data/flick'
import { FlickAvatar } from './FlickAvatar'
import { FlickLiveBadge } from './FlickLiveBadge'

/** Carte de live 16:9 — zoom au survol, badge LIVE, avatar, titre, pays. */
export function FlickLiveCard({ live }: { live: FlickLive }) {
  return (
    <Link to={`/live/${live.id}`} className="group block min-w-0">
      <div className="relative aspect-video overflow-hidden rounded-xl border border-border-default bg-bg-surface transition-all duration-300 group-hover:border-primary-500/40 group-hover:shadow-glow-primary">
        <div
          aria-hidden="true"
          className="flex h-full w-full items-center justify-center text-5xl transition-transform duration-300 group-hover:scale-110"
          style={{ background: `radial-gradient(ellipse at center, hsl(${live.streamer.avatarHue} 60% 16%) 0%, #080C12 75%)` }}
        >
          {live.artwork}
        </div>
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent opacity-60 transition-opacity group-hover:opacity-90" />
        <div className="absolute left-2.5 top-2.5">
          <FlickLiveBadge />
        </div>
        <div className="absolute bottom-2.5 left-2.5 rounded-md bg-black/75 px-2 py-0.5 text-xs font-semibold text-white backdrop-blur">
          {formatViewers(live.viewers)}
        </div>
        <div className="absolute right-2.5 bottom-2.5 rounded-md bg-black/75 px-2 py-0.5 text-[11px] font-medium text-[#94A3B8] backdrop-blur">
          {live.category}
        </div>
      </div>
      <div className="flex gap-2.5 px-0.5 pt-2.5">
        <FlickAvatar name={live.streamer.name} hue={live.streamer.avatarHue} size="sm" />
        <div className="min-w-0 flex-1">
          <h3 className="line-clamp-1 text-sm font-bold text-text-primary transition-colors group-hover:text-primary-400">
            {live.title}
          </h3>
          <p className="mt-0.5 flex items-center gap-1 truncate text-[13px] text-text-secondary">
            <span className="truncate font-medium">{live.streamer.name}</span>
            {live.streamer.verified && <SealCheck size={14} weight="fill" className="shrink-0 text-primary-400" aria-label="Vérifié" />}
          </p>
          <p className="flex items-center gap-1 truncate text-xs text-text-muted">
            <MapPin size={11} />
            {live.streamer.country}
          </p>
        </div>
      </div>
    </Link>
  )
}
