import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface CardProps {
  children: ReactNode
  /** Enlève le padding interne */
  noPadding?: boolean
  className?: string
  onClick?: () => void
}

export function Card({ children, noPadding = false, className, onClick }: CardProps) {
  return (
    <div
      onClick={onClick}
      className={cn(
        'rounded-xl border border-border-default bg-bg-surface',
        'transition-all hover:border-border-hover hover:shadow-lg',
        !noPadding && 'p-4',
        onClick && 'cursor-pointer',
        className,
      )}
    >
      {children}
    </div>
  )
}