import { formatCount } from '../../utils/format'
import { cn } from '../../utils/cn'

interface ViewerCountProps {
  viewers: number
  /** Taille du texte */
  size?: 'sm' | 'md'
  className?: string
}

export function ViewerCount({ viewers, size = 'sm', className }: ViewerCountProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 font-semibold text-text-primary',
        size === 'sm' ? 'text-xs' : 'text-sm',
        className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-live" />
      {formatCount(viewers)} spectateurs
    </span>
  )
}