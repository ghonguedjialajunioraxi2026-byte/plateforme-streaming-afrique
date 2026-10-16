import { useEffect, type ReactNode } from 'react'
import { cn } from '../../utils/cn'

interface ModalProps {
  open: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  /** Largeur max (ex: 'max-w-md') */
  sizeClassName?: string
}

export function Modal({
  open,
  onClose,
  title,
  children,
  sizeClassName = 'max-w-md',
}: ModalProps) {
  useEffect(() => {
    if (!open) return

    function handleKeydown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeydown)
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', handleKeydown)
      document.body.style.overflow = ''
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      role="dialog"
      aria-modal="true"
    >
      {/* Fond assombri */}
      <div
        className="animate-fade-in absolute inset-0 bg-black/60"
        onClick={onClose}
        aria-hidden="true"
      />
      {/* Contenu */}
      <div
        className={cn(
          'animate-scale-in relative z-10 max-h-[90vh] w-full overflow-y-auto rounded-xl border border-border-default bg-bg-surface shadow-xl',
          sizeClassName,
        )}
      >
        <div className="flex items-center justify-between border-b border-border-default px-5 py-4">
          <h2 className="font-display text-lg font-semibold text-text-primary">
            {title}
          </h2>
          <button
            onClick={onClose}
            className="flex h-8 w-8 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            aria-label="Fermer"
          >
            ✕
          </button>
        </div>
        <div className="p-5">{children}</div>
      </div>
    </div>
  )
}