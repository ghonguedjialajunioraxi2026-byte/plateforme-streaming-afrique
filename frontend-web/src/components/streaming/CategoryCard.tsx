import { Link } from 'react-router'
import type { Category } from '../../types/domain'
import { formatCount } from '../../utils/format'

interface CategoryCardProps {
  category: Category
}

/**
 * Box-art de catégorie façon Twitch : vignette portrait (3/4),
 * décalage vert Flick au survol, nom + audience en dessous.
 */
export function CategoryCard({ category }: CategoryCardProps) {
  return (
    <Link
      to={`/recherche?q=${encodeURIComponent(category.name)}`}
      className="group block w-full min-w-0"
    >
      <div className="relative">
        <div
          aria-hidden="true"
          className="absolute inset-0 rounded-lg bg-primary-500 transition-transform duration-200 ease-out group-hover:translate-x-1.5 group-hover:translate-y-1.5"
        />
        <div className="relative aspect-[3/4] overflow-hidden rounded-lg border border-border-default bg-bg-elevated transition-transform duration-200 ease-out group-hover:-translate-x-0.5 group-hover:-translate-y-0.5">
          {category.thumbnailUrl ? (
            <img
              src={category.thumbnailUrl}
              alt={category.name}
              loading="lazy"
              className="h-full w-full object-cover transition-transform duration-300 ease-out group-hover:scale-105"
            />
          ) : (
            <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-primary-500/25 via-bg-elevated to-accent-500/15 text-5xl">
              🎮
            </div>
          )}
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />
          <div className="absolute bottom-2 left-2 rounded-md bg-black/70 px-2 py-0.5 text-xs font-medium text-white">
            {formatCount(category.viewersCount)} spectateurs
          </div>
        </div>
      </div>
      <p className="mt-2 line-clamp-1 text-sm font-semibold text-text-primary transition-colors duration-200 group-hover:text-primary-300">
        {category.name}
      </p>
      <p className="line-clamp-1 text-xs text-text-muted">
        {formatCount(category.streamsCount)} streams en direct
      </p>
    </Link>
  )
}
