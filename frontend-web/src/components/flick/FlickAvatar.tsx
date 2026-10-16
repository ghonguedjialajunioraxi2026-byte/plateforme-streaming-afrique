import { cn } from '../../utils/cn'

interface FlickAvatarProps {
  name: string
  hue?: number
  size?: 'xs' | 'sm' | 'md' | 'lg'
  className?: string
}

const SIZES: Record<NonNullable<FlickAvatarProps['size']>, string> = {
  xs: 'h-6 w-6 text-[10px]',
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-12 w-12 text-base',
}

/** Avatar circulaire FLICK (initiale + teinte créateur). */
export function FlickAvatar({ name, hue = 187, size = 'md', className }: FlickAvatarProps) {
  return (
    <span
      aria-hidden="true"
      style={{ backgroundColor: `hsl(${hue} 60% 22%)`, color: `hsl(${hue} 100% 72%)` }}
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full font-bold ring-1 ring-white/10',
        SIZES[size],
        className,
      )}
    >
      {name.charAt(0).toUpperCase()}
    </span>
  )
}
