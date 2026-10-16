import { useCallback, useEffect, useRef, useState } from 'react'
import type { Category } from '../../types/domain'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'
import { CategoryCard } from './CategoryCard'

interface CategoryRailProps {
  categories: Category[]
  /** Largeur d'une carte (px) — snap + défilement par page */
  cardWidth?: number
  className?: string
}

/**
 * Bandeau de catégories en vedette façon Twitch : défilement horizontal
 * (molette, tactile, snap), flèches précédent/suivant visibles au survol
 * sur desktop, masquées aux extrémités. Grille 8px (gap-3, p-1 pour
 * laisser respirer l'effet de décalage au hover).
 */
export function CategoryRail({ categories, cardWidth = 160, className }: CategoryRailProps) {
  const trackRef = useRef<HTMLDivElement>(null)
  const [canPrev, setCanPrev] = useState(false)
  const [canNext, setCanNext] = useState(false)

  const updateArrows = useCallback(() => {
    const track = trackRef.current
    if (!track) return
    const { scrollLeft, scrollWidth, clientWidth } = track
    setCanPrev(scrollLeft > 4)
    setCanNext(scrollLeft + clientWidth < scrollWidth - 4)
  }, [])

  useEffect(() => {
    updateArrows()
    window.addEventListener('resize', updateArrows)
    return () => window.removeEventListener('resize', updateArrows)
  }, [updateArrows, categories.length])

  function scrollByPage(direction: 1 | -1) {
    const track = trackRef.current
    if (!track) return
    track.scrollBy({ left: direction * track.clientWidth * 0.8, behavior: 'smooth' })
  }

  if (categories.length === 0) return null

  const arrowClass =
    'flex h-9 w-9 items-center justify-center rounded-full border border-border-default bg-bg-secondary/90 text-text-secondary opacity-0 shadow-lg backdrop-blur transition-all duration-200 hover:border-primary-500 hover:text-primary-300 focus-visible:opacity-100 group/rail-hover:opacity-100 disabled:opacity-0'

  return (
    <div className={cn('group/rail-hover relative', className)}>
      <div
        ref={trackRef}
        onScroll={updateArrows}
        className="no-scrollbar -mx-1 flex snap-x snap-mandatory gap-3 overflow-x-auto scroll-smooth px-1 py-1"
      >
        {categories.map((category) => (
          <div
            key={category.id}
            className="shrink-0 snap-start"
            style={{ width: cardWidth }}
          >
            <CategoryCard category={category} />
          </div>
        ))}
      </div>

      {/* Flèches desktop */}
      <button
        type="button"
        onClick={() => scrollByPage(-1)}
        disabled={!canPrev}
        aria-label="Catégories précédentes"
        tabIndex={canPrev ? 0 : -1}
        className={cn(arrowClass, 'absolute -left-3 top-[38%] hidden -translate-y-1/2 md:flex')}
      >
        <Icon name="chevron-down" className="h-5 w-5 rotate-90" />
      </button>
      <button
        type="button"
        onClick={() => scrollByPage(1)}
        disabled={!canNext}
        aria-label="Catégories suivantes"
        tabIndex={canNext ? 0 : -1}
        className={cn(arrowClass, 'absolute -right-3 top-[38%] hidden -translate-y-1/2 md:flex')}
      >
        <Icon name="chevron-down" className="h-5 w-5 -rotate-90" />
      </button>
    </div>
  )
}
