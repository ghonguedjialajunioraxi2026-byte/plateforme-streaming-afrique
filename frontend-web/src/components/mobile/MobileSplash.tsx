import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { FlickLogo } from '../branding'

const SPLASH_MS = 1800

/**
 * Écran de lancement mobile (maquette écran 1) : fond noir, logo FLICK
 * officiel en grand, nom + slogan « Regarde · Partage · Connecte »,
 * barre de progression verte. Avance automatiquement vers l'accueil
 * (bouton « Passer » + tap pour les lecteurs d'écran / reduced motion).
 */
export function MobileSplash() {
  const navigate = useNavigate()
  const [leaving, setLeaving] = useState(false)

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setLeaving(true)
      window.setTimeout(() => navigate('/', { replace: true }), 250)
    }, SPLASH_MS)
    return () => window.clearTimeout(timer)
  }, [navigate])

  return (
    <div
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-bg-primary px-8 transition-opacity duration-200 ${leaving ? 'opacity-0' : 'opacity-100'}`}
      role="status"
      aria-label="Chargement de FLICK"
    >
      <Link to="/" aria-label="FLICK — Accueil" className="flex flex-col items-center">
        <FlickLogo variant="mark" size="lg" link={false} className="[&_img]:h-24" />
        <span className="mt-4 font-display text-4xl font-bold tracking-[0.18em] text-text-primary">
          FLICK
        </span>
      </Link>
      <p className="mt-3 text-sm font-medium text-text-secondary">Regarde · Partage · Connecte</p>
      <div
        aria-hidden="true"
        className="mt-10 h-1 w-24 overflow-hidden rounded-full bg-bg-elevated"
      >
        <div className="h-full w-1/2 animate-splash-bar rounded-full bg-primary-500" />
      </div>
      <button
        type="button"
        onClick={() => navigate('/', { replace: true })}
        className="mt-8 flex min-h-11 items-center rounded-lg px-4 text-sm font-semibold text-text-muted transition-colors active:text-text-primary"
      >
        Passer
      </button>
    </div>
  )
}
