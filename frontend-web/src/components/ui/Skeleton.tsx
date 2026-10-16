import { cn } from '../../utils/cn'

interface SkeletonProps {
  className?: string
}

/**
 * Bloc de chargement avec balayage lumineux (shimmer), signature
 * des interfaces type Twitch pendant le chargement des données.
 */
export function Skeleton({ className }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        'animate-shimmer rounded-lg bg-[linear-gradient(100deg,var(--color-bg-elevated)_40%,var(--color-bg-hover)_50%,var(--color-bg-elevated)_60%)] bg-[length:200%_100%]',
        className,
      )}
    />
  )
}