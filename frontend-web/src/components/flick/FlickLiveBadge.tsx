import { cn } from '../../utils/cn'

/** Badge LIVE rouge impulsion avec onde pulse. */
export function FlickLiveBadge({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md bg-live px-2 py-1 text-[11px] font-bold tracking-wider text-white uppercase shadow-glow-live',
        className,
      )}
    >
      <span className="relative flex h-1.5 w-1.5">
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
        <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-white" />
      </span>
      Live
    </span>
  )
}
