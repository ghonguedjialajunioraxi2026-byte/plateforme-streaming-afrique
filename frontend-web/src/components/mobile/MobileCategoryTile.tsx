import { Link } from 'react-router'
import type { Category } from '../../types/domain'
import { SIDEBAR_CATEGORIES } from '../../constants/discover'
import { formatCount } from '../../utils/format'
import { cn } from '../../utils/cn'
import { Icon, type IconName } from '../ui/icons'

/** Teintes des pastilles d'icônes (maquette écran Catégories). */
const TILE_TINTS: Record<string, string> = {
  Gaming: 'bg-primary-500/15 text-primary-400',
  Musique: 'bg-[#a855f7]/15 text-[#c084fc]',
  Sport: 'bg-[#38bdf8]/15 text-[#7dd3fc]',
  Discussion: 'bg-[#f472b6]/15 text-[#f9a8d4]',
  Podcast: 'bg-[#fb923c]/15 text-[#fdba74]',
  Éducation: 'bg-[#facc15]/15 text-[#fde047]',
  Education: 'bg-[#facc15]/15 text-[#fde047]',
  Technologie: 'bg-[#22d3ee]/15 text-[#67e8f9]',
  Art: 'bg-[#fb7185]/15 text-[#fda4af]',
  IRL: 'bg-[#34d399]/15 text-[#6ee7b7]',
  Cuisine: 'bg-[#fbbf24]/15 text-[#fcd34d]',
  Business: 'bg-[#94a3b8]/15 text-[#cbd5e1]',
}

function iconFor(name: string): IconName {
  const match = SIDEBAR_CATEGORIES.find(
    (candidate) => candidate.name.toLowerCase() === name.toLowerCase(),
  )
  return match?.icon ?? 'grid'
}

function tintFor(name: string): string {
  return TILE_TINTS[name] ?? 'bg-primary-500/15 text-primary-400'
}

/**
 * Tuile de catégorie mobile (maquette écran 6) : carte sombre arrondie,
 * pastille d'icône teintée, nom + audience « X en ligne ».
 */
export function MobileCategoryTile({ category, className }: { category: Category; className?: string }) {
  return (
    <Link
      to={`/recherche?q=${encodeURIComponent(category.name)}`}
      className={cn(
        'flex min-h-[104px] flex-col items-center justify-center gap-1.5 rounded-2xl border border-border-default bg-bg-surface px-2 py-3 text-center transition-colors active:border-border-hover active:bg-bg-elevated',
        className,
      )}
    >
      <span
        aria-hidden="true"
        className={cn('flex h-10 w-10 items-center justify-center rounded-full', tintFor(category.name))}
      >
        <Icon name={iconFor(category.name)} className="h-5 w-5" />
      </span>
      <span className="line-clamp-1 w-full text-[13px] font-semibold text-text-primary">
        {category.name}
      </span>
      <span className="line-clamp-1 w-full text-[11px] text-text-muted">
        {category.viewersCount > 0
          ? `${formatCount(category.viewersCount)} en ligne`
          : category.streamsCount > 0
            ? `${formatCount(category.streamsCount)} lives`
            : 'Explorer'}
      </span>
    </Link>
  )
}
