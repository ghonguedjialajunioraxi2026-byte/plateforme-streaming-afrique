import { Link } from 'react-router'
import { cn } from '../../utils/cn'

interface MobileSectionHeaderProps {
  title: string
  /** Lien « Voir tout → » (vert FLICK, façon maquette) */
  seeAllTo?: string
  seeAllLabel?: string
  className?: string
}

/**
 * En-tête de section mobile : titre blanc audacieux à gauche,
 * lien « Voir tout → » vert à droite (maquette Accueil/Explorer).
 */
export function MobileSectionHeader({
  title,
  seeAllTo,
  seeAllLabel = 'Voir tout',
  className,
}: MobileSectionHeaderProps) {
  return (
    <div className={cn('flex items-baseline justify-between gap-3', className)}>
      <h2 className="font-display text-[17px] font-bold tracking-tight text-text-primary">
        {title}
      </h2>
      {seeAllTo !== undefined && (
        <Link
          to={seeAllTo}
          className="flex min-h-11 shrink-0 items-center gap-0.5 text-[13px] font-semibold text-primary-400 transition-colors active:text-primary-300"
        >
          {seeAllLabel}
          <span aria-hidden="true">→</span>
        </Link>
      )}
    </div>
  )
}
