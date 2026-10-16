import { Link } from 'react-router'
import { ArrowRight } from '@phosphor-icons/react'

interface FlickSectionProps {
  title: string
  subtitle?: string
  seeAllTo?: string
  seeAllLabel?: string
  children?: React.ReactNode
}

/** En-tête de section façon Netflix/Twitch (titre + voir tout). */
export function FlickSection({ title, subtitle, seeAllTo, seeAllLabel = 'Tout voir', children }: FlickSectionProps) {
  return (
    <section aria-label={title} className="space-y-4">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h2 className="flex items-center gap-2.5 font-display text-xl font-bold tracking-tight text-text-primary">
            <span aria-hidden="true" className="h-6 w-1 rounded-full bg-primary-500 shadow-glow-primary" />
            {title}
          </h2>
          {subtitle && <p className="mt-1 pl-3.5 text-sm text-text-secondary">{subtitle}</p>}
        </div>
        {seeAllTo && (
          <Link
            to={seeAllTo}
            className="flex shrink-0 items-center gap-1 rounded-lg px-2 py-1 text-sm font-semibold text-primary-500 hover:bg-primary-500/10"
          >
            {seeAllLabel}
            <ArrowRight size={15} weight="bold" />
          </Link>
        )}
      </div>
      {children}
    </section>
  )
}
