import { cn } from '../../utils/cn'

interface LoaderProps {
  /** Taille du spinner */
  size?: 'sm' | 'md' | 'lg'
  className?: string
}

const sizeClasses = {
  sm: 'h-4 w-4 border-2',
  md: 'h-8 w-8 border-[3px]',
  lg: 'h-12 w-12 border-4',
} as const

/** Spinner de chargement centré. */
export function Loader({ size = 'md', className }: LoaderProps) {
  return (
    <div
      role="status"
      aria-label="Chargement"
      className={cn(
        'animate-spin rounded-full border-border-default border-t-primary-500',
        sizeClasses[size],
        className,
      )}
    />
  )
}