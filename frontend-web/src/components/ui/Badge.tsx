import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

type BadgeVariant = 'live' | 'primary' | 'accent' | 'neutral' | 'danger' | 'warning'

interface BadgeProps {
  variant?: BadgeVariant
  /** Affiche un point clignotant (ex: LIVE) */
  dot?: boolean
  children: ReactNode
  className?: string
}

const variantClasses: Record<BadgeVariant, string> = {
  live: 'bg-live text-white',
  primary: 'bg-primary-500/20 text-primary-400',
  accent: 'bg-accent-500/20 text-accent-400',
  neutral: 'bg-bg-elevated text-text-secondary',
  danger: 'bg-danger/20 text-danger',
  warning: 'bg-warning/20 text-warning',
}

export function Badge({ variant = 'neutral', dot = false, children, className }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold',
        variantClasses[variant],
        className,
      )}
    >
      {dot && (
        <span className="h-1.5 w-1.5 animate-pulse-live rounded-full bg-current" />
      )}
      {children}
    </span>
  )
}