import { Link } from 'react-router'
import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface MobileChipProps {
  /** Libellé affiché */
  label: string
  /** État actif (pastille verte) */
  active?: boolean
  /** Lien de navigation (chip = lien) */
  to?: string
  /** Action (chip = bouton, ex : filtre local) */
  onClick?: () => void
  /** Compteur optionnel */
  count?: number
}

/**
 * Pastille de filtre mobile façon maquette (Explorer, Recherche,
 * Vidéos, Notifications) : inactive = fond sombre + texte gris,
 * active = vert FLICK + texte sombre. Zone tactile ≥ 44px de haut.
 */
export function MobileChip({ label, active = false, to, onClick, count }: MobileChipProps) {
  const className = cn(
    'flex h-11 shrink-0 snap-start items-center gap-1.5 rounded-full px-4 text-sm font-semibold transition-colors duration-200 active:scale-95',
    active
      ? 'bg-primary-500 text-text-inverse shadow-glow-primary'
      : 'bg-bg-elevated text-text-secondary active:bg-bg-hover active:text-text-primary',
  )
  const content = (
    <>
      {label}
      {count !== undefined && count > 0 && (
        <span
          className={cn(
            'flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-[11px] font-bold',
            active ? 'bg-black/25 text-text-inverse' : 'bg-primary-500 text-text-inverse',
          )}
        >
          {count > 99 ? '99+' : count}
        </span>
      )}
    </>
  )

  if (to !== undefined) {
    return (
      <Link to={to} aria-pressed={active} className={className}>
        {content}
      </Link>
    )
  }
  return (
    <button type="button" onClick={onClick} aria-pressed={active} className={className}>
      {content}
    </button>
  )
}

/**
 * Rail horizontal de chips (snap tactile, sans scrollbar visible),
 * utilisé pour les filtres des pages mobiles.
 */
export function MobileChipRail({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div
      role="group"
      aria-label={label}
      className="no-scrollbar -mx-3 flex snap-x snap-mandatory gap-2 overflow-x-auto px-3 py-1"
    >
      {children}
    </div>
  )
}
