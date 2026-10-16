import { Link } from 'react-router'
import { Eye } from '@phosphor-icons/react'
import type { FlickCategory } from '../../types/flick'
import { formatViewers } from '../../data/flick'
import { cn } from '../../utils/cn'

/** Carte catégorie visuelle — fond artwork + dégradé + viewers en ligne. */
export function FlickCategoryCard({ category }: { category: FlickCategory }) {
  return (
    <Link
      to={`/recherche?q=${encodeURIComponent(category.name)}`}
      className="group relative block overflow-hidden rounded-xl border border-border-default bg-bg-surface transition-all duration-300 hover:border-primary-500/40 hover:shadow-glow-primary"
    >
      <div className={cn('relative flex h-28 items-center justify-center bg-gradient-to-br text-4xl', category.gradient)}>
        <span aria-hidden="true" className="transition-transform duration-300 group-hover:scale-125">{category.artwork}</span>
        <div className="absolute inset-0 bg-gradient-to-t from-[#05070A] via-transparent to-transparent" />
      </div>
      <div className="p-3">
        <p className="truncate text-sm font-bold text-text-primary group-hover:text-primary-400">{category.name}</p>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-text-secondary">
          <Eye size={13} className="text-danger" />
          {formatViewers(category.viewers)} · {category.lives} lives
        </p>
      </div>
    </Link>
  )
}
