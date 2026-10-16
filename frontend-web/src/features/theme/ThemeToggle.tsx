import { Moon, Sun } from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import { useTheme } from './ThemeContext'

interface ThemeToggleProps {
  className?: string
  /** Taille tactile confortable (mobile) */
  size?: 'md' | 'lg'
}

/** Bascule sombre/clair FLICK (persistée, instantanée). */
export function ThemeToggle({ className, size = 'md' }: ThemeToggleProps) {
  const { theme, toggle } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? 'Passer en mode clair' : 'Passer en mode sombre'}
      aria-pressed={!isDark}
      title={isDark ? 'Mode clair' : 'Mode sombre'}
      className={cn(
        'flex items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary',
        size === 'lg' ? 'h-11 w-11' : 'h-10 w-10',
        className,
      )}
    >
      {isDark ? <Sun size={20} /> : <Moon size={20} />}
    </button>
  )
}
