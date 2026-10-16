import { useState } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'
import {
  BRAND_ALT,
  BRAND_HOME_PATH,
  BRAND_LOGO_SRC,
  BRAND_MARK_SRC,
} from '../../constants/branding'

export type FlickLogoSize = 'sm' | 'md' | 'lg'
export type FlickLogoVariant = 'full' | 'mark'

interface FlickLogoProps {
  /** Taille du logo (hauteur fixe, largeur auto → proportions conservées). */
  size?: FlickLogoSize
  /**
   * `full` = logo standard · `mark` = usage compact (sidebar, promos).
   * Les deux affichent le logo officiel `FLICK.png` (source unique).
   */
  variant?: FlickLogoVariant
  /** Lien vers l'accueil autour du logo (désactiver dans les cartes/promos). */
  link?: boolean
  className?: string
}

/// Hauteur d'affichage par taille — `w-auto` + `object-contain` :
/// le logo n'est jamais déformé, quelle que soit la variante.
const heights: Record<FlickLogoSize, string> = {
  sm: 'h-7',
  md: 'h-8',
  lg: 'h-10',
}

/**
 * Logo officiel FLICK — SEUL composant autorisé à afficher la marque.
 * Source unique : `src/assets/branding/FLICK.png` (via `src/constants/branding.ts`).
 * Remplacer ce fichier suffit à mettre à jour toute l'application.
 */
export function FlickLogo({ size = 'md', variant = 'full', link = true, className }: FlickLogoProps) {
  const [failed, setFailed] = useState(false)
  const src = variant === 'mark' ? BRAND_MARK_SRC : BRAND_LOGO_SRC

  // Repli si l'image ne charge pas : texte accessible, PAS un logo redessiné.
  if (failed) {
    const fallback = (
      <span
        aria-label={BRAND_ALT}
        className={cn(
          'inline-flex items-center font-display text-lg font-bold text-text-primary',
          heights[size],
        )}
      >
        {BRAND_ALT}
      </span>
    )
    return link ? (
      <Link to={BRAND_HOME_PATH} aria-label={`${BRAND_ALT} — Accueil`} className={cn('inline-flex items-center rounded-lg', className)}>
        {fallback}
      </Link>
    ) : (
      <span className={cn('inline-flex items-center', className)}>{fallback}</span>
    )
  }

  const image = (
    <img
      src={src}
      alt={BRAND_ALT}
      draggable={false}
      onError={() => setFailed(true)}
      className={cn(heights[size], 'w-auto shrink-0 object-contain')}
    />
  )

  if (!link) {
    return <span className={cn('inline-flex items-center', className)}>{image}</span>
  }

  return (
    <Link
      to={BRAND_HOME_PATH}
      aria-label={`${BRAND_ALT} — Accueil`}
      className={cn('inline-flex items-center rounded-lg', className)}
    >
      {image}
    </Link>
  )
}
