import { useState } from 'react'
import { Link } from 'react-router'
import { SealCheck, MapPin, Plus, Check } from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import type { FlickCreator } from '../../types/flick'
import { FlickAvatar } from './FlickAvatar'

/** Carte profil créateur émergent + bouton Suivre interactif. */
export function FlickCreatorCard({ creator }: { creator: FlickCreator }) {
  const [following, setFollowing] = useState(false)

  return (
    <div className="flex items-center gap-3 rounded-xl border border-border-default bg-bg-surface p-3.5 transition-colors hover:border-border-hover">
      <Link to={`/channel/${creator.slug}`} className="relative shrink-0">
        <FlickAvatar name={creator.name} hue={creator.avatarHue} size="lg" />
        {creator.liveNow && (
          <span className="absolute -right-0.5 -bottom-0.5 h-3.5 w-3.5 rounded-full border-[3px] border-bg-surface bg-live" aria-label="En direct" />
        )}
      </Link>
      <div className="min-w-0 flex-1">
        <Link to={`/channel/${creator.slug}`} className="flex items-center gap-1 truncate font-bold text-text-primary hover:text-primary-400">
          <span className="truncate">{creator.name}</span>
          {creator.verified && <SealCheck size={15} weight="fill" className="shrink-0 text-primary-400" aria-label="Vérifié" />}
        </Link>
        <p className="line-clamp-1 text-[13px] text-text-secondary">{creator.headline}</p>
        <p className="mt-0.5 flex items-center gap-1 text-xs text-text-muted">
          <MapPin size={11} />
          {creator.country} · {(creator.followers / 1000).toFixed(0)}K abonnés
        </p>
      </div>
      <button
        type="button"
        onClick={() => setFollowing((v) => !v)}
        aria-pressed={following}
        className={cn(
          'flex h-9 shrink-0 items-center gap-1 rounded-lg px-3 text-sm font-bold transition-all',
          following
            ? 'border border-primary-500/40 bg-primary-500/10 text-primary-500'
            : 'bg-primary-500 text-black hover:bg-primary-300',
        )}
      >
        {following ? <Check size={15} weight="bold" /> : <Plus size={15} weight="bold" />}
        {following ? 'Suivi' : 'Suivre'}
      </button>
    </div>
  )
}
