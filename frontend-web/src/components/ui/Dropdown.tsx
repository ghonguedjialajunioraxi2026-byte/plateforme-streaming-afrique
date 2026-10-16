import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'

const DropdownCloseContext = createContext<() => void>(() => {})

interface DropdownProps {
  trigger: ReactNode
  children: ReactNode
  /** Largeur du menu (ex: 'w-52') */
  menuClassName?: string
  /** Alignement horizontal du menu */
  align?: 'left' | 'right'
}

export function Dropdown({ trigger, children, menuClassName, align = 'right' }: DropdownProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const close = () => setOpen(false)

  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) close()
    }
    function handleKeydown(event: KeyboardEvent) {
      if (event.key === 'Escape') close()
    }
    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeydown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeydown)
    }
  }, [open])

  return (
    <DropdownCloseContext.Provider value={close}>
      <div ref={ref} className="relative">
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-haspopup="menu"
          aria-expanded={open}
          className="flex items-center rounded-lg transition-opacity hover:opacity-90"
        >
          {trigger}
        </button>
        {open && (
          <div
            role="menu"
            className={cn(
              'absolute z-40 mt-2 min-w-52 overflow-hidden rounded-xl border border-border-default bg-bg-elevated py-1 shadow-xl',
              align === 'right' ? 'right-0' : 'left-0',
              menuClassName,
            )}
          >
            {children}
          </div>
        )}
      </div>
    </DropdownCloseContext.Provider>
  )
}

export interface DropdownItemProps {
  /** Si fourni, rend un lien React Router ; sinon un bouton */
  to?: string
  onClick?: () => void
  icon?: ReactNode
  danger?: boolean
  children: ReactNode
  className?: string
}

export function DropdownItem({
  to,
  onClick,
  icon,
  danger = false,
  children,
  className,
}: DropdownItemProps) {
  const close = useContext(DropdownCloseContext)

  const classes = cn(
    'flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-sm transition-colors',
    danger
      ? 'text-danger hover:bg-danger/10'
      : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
    className,
  )

  const content = (
    <>
      {icon && <span className="shrink-0 opacity-80">{icon}</span>}
      <span className="truncate">{children}</span>
    </>
  )

  if (to) {
    return (
      <Link to={to} role="menuitem" className={classes} onClick={close}>
        {content}
      </Link>
    )
  }
  return (
    <button type="button" role="menuitem" className={classes} onClick={() => {
      onClick?.()
      close()
    }}>
      {content}
    </button>
  )
}

export function DropdownSeparator() {
  return <div role="separator" className="my-1 border-t border-border-default" />
}