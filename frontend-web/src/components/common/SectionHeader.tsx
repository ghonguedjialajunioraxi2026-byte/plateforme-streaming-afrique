import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'

interface SectionHeaderProps {
  title: string
  subtitle?: string
  /** Route du lien "Voir tout" (caché si absent) */
  seeAllTo?: string
  /** Zone d'action libre à droite (remplace le lien "Voir tout") */
  action?: ReactNode
  className?: string
  /**
   * Titre couleur accent (vert FLICK) façon Twitch : les titres de
   * sections découverte sont des liens colorés, pas du blanc.
   * @default false
   */
  accent?: boolean
}

/** Titre de section homogène sur toutes les pages (Home, Explorer…).
 *  Style Twitch : titre affirmé, lien « voir plus » vert souligné au survol. */
export function SectionHeader({ title, subtitle, seeAllTo, action, className, accent = false }: SectionHeaderProps) {
  return (
    <div className={cn('mb-4 flex flex-wrap items-end justify-between gap-3', className)}>
      <div>
        <h2
          className={cn(
            'font-display text-xl font-bold tracking-tight',
            accent ? 'text-primary-400' : 'text-text-primary',
          )}
        >
          {title}
        </h2>
        {subtitle && <p className="mt-0.5 text-sm text-text-secondary">{subtitle}</p>}
      </div>
      {action ??
        (seeAllTo && (
          <Link
            to={seeAllTo}
            className="rounded-md px-1 py-0.5 text-sm font-semibold text-primary-400 underline-offset-4 transition-colors duration-200 hover:text-primary-300 hover:underline"
          >
            Voir plus
          </Link>
        ))}
    </div>
  )
}