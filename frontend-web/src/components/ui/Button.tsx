import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../utils/cn'
import { Loader } from './Loader'

type Variant = 'primary' | 'secondary' | 'accent' | 'danger' | 'ghost'
type Size = 'sm' | 'md' | 'lg'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  /** Affiche un spinner et désactive le bouton */
  loading?: boolean
  fullWidth?: boolean
}

const variantClasses: Record<Variant, string> = {
  primary:
    'bg-primary-500 text-text-inverse font-semibold hover:bg-primary-400 hover:shadow-glow-primary',
  secondary:
    'bg-bg-surface text-text-primary border border-border-default font-medium hover:border-border-hover hover:bg-bg-elevated',
  accent:
    'bg-accent-500 text-text-inverse font-semibold hover:bg-accent-400',
  danger: 'bg-danger text-white font-semibold hover:bg-danger-dark',
  ghost:
    'text-text-secondary font-medium hover:bg-bg-hover hover:text-text-primary',
}

const sizeClasses: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
  lg: 'px-6 py-3 text-base',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  fullWidth = false,
  className,
  disabled,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-lg transition-all',
        'disabled:cursor-not-allowed disabled:opacity-60',
        variantClasses[variant],
        sizeClasses[size],
        fullWidth && 'w-full',
        className,
      )}
      {...props}
    >
      {loading && <Loader size="sm" className="border-text-inverse/40 border-t-white" />}
      {children}
    </button>
  )
}