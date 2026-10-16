import { useEffect, useId, useRef, type ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { Icon } from './icons'

interface BottomSheetProps {
  open: boolean
  onClose: () => void
  /** Titre affiché dans l'en-tête de la feuille */
  title?: string
  children: ReactNode
  /** Hauteur max de la feuille (défaut : 85vh) */
  maxHeightClassName?: string
  className?: string
}

/**
 * Bottom sheet mobile — panneau remontant depuis le bas (filtres,
 * partage, options, menu). Sur desktop (`sm:`), se comporte comme
 * un dialogue centré compact. Accessible : `Escape` ferme, clic sur
 * l'overlay ferme, `aria-modal`, titre lié, scroll tactile contenu,
 * poignée visuelle. Ziel tactile de fermeture ≥ 44px.
 */
export function BottomSheet({
  open,
  onClose,
  title,
  children,
  maxHeightClassName = 'max-h-[85vh]',
  className,
}: BottomSheetProps) {
  const titleId = useId()
  const panelRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleKeydown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeydown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // Focus initial sur le panneau pour les lecteurs d'écran.
    panelRef.current?.focus({ preventScroll: true })
    return () => {
      document.removeEventListener('keydown', handleKeydown)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 sm:flex sm:items-center sm:justify-center sm:p-4" role="dialog" aria-modal="true" aria-labelledby={title !== undefined ? titleId : undefined}>
      {/* Overlay */}
      <button
        type="button"
        aria-label="Fermer le panneau"
        onClick={onClose}
        className="animate-fade-in absolute inset-0 cursor-default bg-black/60"
      />
      {/* Panneau : feuille basse sur mobile, carte centrée sur desktop */}
      <div
        ref={panelRef}
        tabIndex={-1}
        className={cn(
          'animate-sheet-up absolute inset-x-0 bottom-0 flex w-full flex-col overflow-hidden rounded-t-2xl border-t border-border-default bg-bg-secondary shadow-xl outline-none',
          'sm:relative sm:inset-auto sm:w-full sm:max-w-md sm:rounded-xl sm:border',
          'pb-[env(safe-area-inset-bottom)]',
          maxHeightClassName,
          className,
        )}
      >
        {/* Poignée (mobile uniquement) */}
        <div aria-hidden="true" className="flex shrink-0 justify-center pt-2.5 sm:hidden">
          <span className="h-1 w-10 rounded-full bg-border-hover" />
        </div>
        {(title !== undefined) && (
          <div className="flex min-h-11 shrink-0 items-center justify-between gap-2 px-4 py-2">
            <h2 id={titleId} className="font-display text-base font-bold tracking-tight text-text-primary">
              {title}
            </h2>
            <button
              type="button"
              onClick={onClose}
              aria-label="Fermer le panneau"
              className="flex h-11 w-11 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary active:bg-bg-hover"
            >
              <Icon name="x" className="h-5 w-5" />
            </button>
          </div>
        )}
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-4 pb-4">
          {children}
        </div>
      </div>
    </div>
  )
}
