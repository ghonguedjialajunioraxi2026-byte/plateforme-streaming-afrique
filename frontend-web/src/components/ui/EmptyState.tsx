import type { ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { Icon, type IconName } from './icons'

interface EmptyStateProps {
  /** Icône illustrative (système maison, currentColor) */
  icon?: IconName
  /** Titre — ex. « Aucun stream en direct » */
  title: string
  /** Explication + suggestion d'action */
  description?: string
  /** Bouton d'action (ex. « Explorer les catégories ») */
  action?: ReactNode
  className?: string
}

/**
 * État vide travaillé — jamais un écran blanc : icône dans un médaillon,
 * titre, explication et action de sortie. Utilisé partout (accueil,
 * recherche, suivis, chat, VODs…).
 */
export function EmptyState({ icon = 'video', title, description, action, className }: EmptyStateProps) {
  return (
    <div
      className={cn(
        'animate-fade-in flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-border-hover bg-bg-surface px-6 py-12 text-center',
        className,
      )}
    >
      <span className="flex h-14 w-14 items-center justify-center rounded-full bg-bg-elevated text-text-muted">
        <Icon name={icon} className="h-7 w-7" />
      </span>
      <p className="font-display text-base font-semibold text-text-primary">{title}</p>
      {description && <p className="max-w-sm text-sm text-text-secondary">{description}</p>}
      {action}
    </div>
  )
}
