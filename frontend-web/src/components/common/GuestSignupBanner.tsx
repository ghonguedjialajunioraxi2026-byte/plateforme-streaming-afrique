import { useState } from 'react'
import { Link } from 'react-router'
import { FlickLogo } from '../branding'
import { Icon } from '../ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { APP_NAME } from '../../constants/app'

const DISMISS_KEY = 'flick-guest-banner-dismissed'

function initiallyVisible(): boolean {
  try {
    return window.localStorage.getItem(DISMISS_KEY) !== '1'
  } catch {
    return true
  }
}

/**
 * Bannière d'inscription pour les visiteurs (barre basse façon
 * Twitch, en vert FLICK) : logo + accroche + CTA S'inscrire,
 * refermable (mémorisé). Rendue uniquement aux visiteurs.
 * Sur mobile, elle flotte au-dessus de la bottom nav.
 */
export function GuestSignupBanner() {
  const { isAuthenticated, isLoading } = useAuth()
  const [visible, setVisible] = useState<boolean>(initiallyVisible)

  if (isLoading || isAuthenticated || !visible) return null

  function dismiss() {
    try {
      window.localStorage.setItem(DISMISS_KEY, '1')
    } catch {
      // Stockage indisponible : la bannière reviendra à la prochaine visite.
    }
    setVisible(false)
  }

  return (
    <>
      {/* Réserve l'espace (bannière fixe) : + hauteur de la bottom nav sur mobile */}
      <div aria-hidden="true" className="h-[124px] shrink-0 md:h-[60px]" />
    <div
      role="region"
      aria-label={`Rejoindre ${APP_NAME}`}
      className="fixed inset-x-0 bottom-16 z-20 border-y border-primary-500/40 bg-primary-500 pb-[env(safe-area-inset-bottom)] md:bottom-0"
    >
      <div className="mx-auto flex w-full max-w-[1600px] items-center gap-3 px-3 py-2.5 lg:px-4">
          <FlickLogo variant="mark" size="sm" link={false} className="hidden shrink-0 sm:block" />
          <p className="min-w-0 flex-1 truncate text-sm text-text-inverse">
            <span className="font-bold">Rejoins la communauté {APP_NAME} !</span>{' '}
            <span className="hidden sm:inline">
              Découvre les meilleurs streams où que tu sois.
            </span>
          </p>
          <Link
            to="/inscription"
            className="flex h-9 shrink-0 items-center rounded-lg bg-bg-primary px-4 text-sm font-bold text-text-primary transition-transform duration-200 active:scale-95"
          >
            S&apos;inscrire
          </Link>
          <button
            type="button"
            onClick={dismiss}
            aria-label="Masquer la bannière d'inscription"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-text-inverse/80 transition-colors hover:bg-black/20 hover:text-text-inverse"
          >
            <Icon name="x" className="h-4 w-4" />
          </button>
        </div>
    </div>
    </>
  )
}
