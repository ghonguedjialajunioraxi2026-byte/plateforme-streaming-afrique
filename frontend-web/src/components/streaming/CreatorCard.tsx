import { Link } from 'react-router'
import type { Channel } from '../../types/domain'
import { Avatar, Badge } from '../ui'
import { formatCount } from '../../utils/format'

interface CreatorCardProps {
  channel: Channel
}

export function CreatorCard({ channel }: CreatorCardProps) {
  return (
    <Link
      to={`/channel/${channel.slug}`}
      className="group flex items-center gap-3 rounded-xl border border-border-default bg-bg-surface p-3 transition-all duration-200 ease-out hover:-translate-y-0.5 hover:border-primary-500/50 hover:shadow-glow-primary"
    >
      <Avatar name={channel.name} size="md" />
      <div className="min-w-0 flex-1">
        <p className="flex items-center gap-2 font-semibold text-text-primary line-clamp-1 transition-colors duration-200 group-hover:text-primary-300">
          {channel.name}
          {channel.isLive && <Badge variant="live" dot>LIVE</Badge>}
        </p>
        <p className="text-sm text-text-muted">
          {formatCount(channel.followersCount)} abonnés
        </p>
      </div>
    </Link>
  )
}