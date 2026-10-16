import { Skeleton } from '../ui'
import { cn } from '../../utils/cn'

/**
 * Skeletons fidèles à la forme des cartes (mêmes ratios, mêmes
 * espacements) pour un chargement sans saut de mise en page.
 * Grille 8px respectée : gaps et paddings identiques aux vraies cartes.
 */

export function LiveCardSkeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn('overflow-hidden rounded-lg border border-border-default bg-bg-surface', className)}
    >
      <Skeleton className="aspect-video w-full rounded-none" />
      <div className="flex gap-2.5 p-3">
        <Skeleton className="h-9 w-9 shrink-0 rounded-full" />
        <div className="min-w-0 flex-1 space-y-2 pt-0.5">
          <Skeleton className="h-4 w-11/12" />
          <Skeleton className="h-3 w-2/3" />
          <Skeleton className="h-3 w-1/3" />
        </div>
      </div>
    </div>
  )
}

export function CategoryCardSkeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn('overflow-hidden rounded-lg border border-border-default bg-bg-surface', className)}
    >
      <Skeleton className="aspect-[3/4] w-full rounded-none" />
      <div className="space-y-2 p-3">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-3 w-1/2" />
      </div>
    </div>
  )
}

export function CreatorCardSkeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn('flex items-center gap-3 rounded-lg border border-border-default bg-bg-surface p-3', className)}
    >
      <Skeleton className="h-12 w-12 shrink-0 rounded-full" />
      <div className="min-w-0 flex-1 space-y-2">
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-3 w-1/2" />
      </div>
      <Skeleton className="h-8 w-20 shrink-0" />
    </div>
  )
}
