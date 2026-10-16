import { Badge } from '../ui'
import { cn } from '../../utils/cn'

interface LiveBadgeProps {
  size?: 'sm' | 'md'
  className?: string
}

export function LiveBadge({ size = 'sm', className }: LiveBadgeProps) {
  return (
    <Badge
      variant="live"
      dot
      className={cn('rounded-md', size === 'md' && 'px-3 py-1 text-sm', className)}
    >
      LIVE
    </Badge>
  )
}
