import type { ReactNode } from 'react'

interface TooltipProps {
  label: string
  children: ReactNode
}

/** Infobulle simple affichée au survol. */
export function Tooltip({ label, children }: TooltipProps) {
  return (
    <span className="group relative inline-flex">
      {children}
      <span
        role="tooltip"
        className="pointer-events-none absolute -top-9 left-1/2 z-50 -translate-x-1/2 whitespace-nowrap rounded-md bg-bg-elevated px-2 py-1 text-xs text-text-primary opacity-0 shadow-lg transition-opacity group-hover:opacity-100"
      >
        {label}
      </span>
    </span>
  )
}