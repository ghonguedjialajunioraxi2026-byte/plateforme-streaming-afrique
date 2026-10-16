import { NavLink } from 'react-router'
import { cn } from '../../utils/cn'
import { Icon, type IconName } from '../ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { useUnreadCount } from '../../hooks/useUnreadCount'

interface BottomNavItem {
  to: string
  label: string
  icon: IconName
  end?: boolean
}

const AUTH_ITEMS: BottomNavItem[] = [
  { to: '/', label: 'Accueil', icon: 'home', end: true },
  { to: '/explorer', label: 'Explorer', icon: 'compass' },
  { to: '/notifications', label: 'Notifications', icon: 'bell' },
  { to: '/profile', label: 'Profil', icon: 'user' },
]

const GUEST_ITEMS: BottomNavItem[] = [
  { to: '/', label: 'Accueil', icon: 'home', end: true },
  { to: '/explorer', label: 'Explorer', icon: 'compass' },
  { to: '/recherche', label: 'Recherche', icon: 'search' },
  { to: '/connexion', label: 'Connexion', icon: 'user' },
]

function NavCell({ item, badge }: { item: BottomNavItem; badge?: number }) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        cn(
          'relative flex min-h-11 flex-col items-center justify-center gap-1 py-2.5 text-[11px] font-medium transition-colors duration-200',
          isActive ? 'text-primary-400' : 'text-text-muted active:text-text-primary',
        )
      }
    >
      {({ isActive }) => (
        <>
          {isActive && (
            <span
              aria-hidden="true"
              className="absolute inset-x-8 top-0 h-0.5 rounded-full bg-primary-500"
            />
          )}
          <span className="relative">
            <Icon name={item.icon} className="h-5 w-5" />
            {badge !== undefined && badge > 0 && (
              <span className="absolute -right-2 -top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
                {badge > 9 ? '9+' : badge}
              </span>
            )}
          </span>
          <span>{item.label}</span>
        </>
      )}
    </NavLink>
  )
}

/**
 * Barre de navigation inférieure mobile (maquette : Accueil · Explorer ·
 * Créer · Notifications · Profil) :
 * - connecté : Accueil · Explorer · [Créer] · Notifications · Profil, le bouton
 *   Créer central (pastille verte surélevée) mène au Stream Manager ;
 * - visiteur : Accueil · Explorer · Recherche · Connexion.
 * Zone tactile ≥ 44px, safe-area iOS, pilule verte d'état actif.
 * Visible sous `md` uniquement.
 */
export function MobileBottomNav() {
  const { isAuthenticated } = useAuth()
  const unread = useUnreadCount()

  if (!isAuthenticated) {
    return (
      <nav
        aria-label="Navigation mobile"
        className="fixed inset-x-0 bottom-0 z-30 border-t border-border-default bg-bg-secondary/95 backdrop-blur-md md:hidden"
      >
        <div className="grid grid-cols-4 pb-[env(safe-area-inset-bottom)]">
          {GUEST_ITEMS.map((item) => (
            <NavCell key={item.to} item={item} />
          ))}
        </div>
      </nav>
    )
  }

  return (
    <nav
      aria-label="Navigation mobile"
      className="fixed inset-x-0 bottom-0 z-30 border-t border-border-default bg-bg-secondary/95 backdrop-blur-md md:hidden"
    >
      <div className="grid grid-cols-5 items-start pb-[env(safe-area-inset-bottom)]">
        <NavCell item={AUTH_ITEMS[0]} />
        <NavCell item={AUTH_ITEMS[1]} />
        {/* Bouton Créer central — pastille verte surélevée, ≥ 48px */}
        <NavLink
          to="/creator/stream"
          aria-label="Créer un stream"
          className="flex flex-col items-center gap-1 py-1.5 text-[11px] font-medium text-text-secondary"
        >
          <span className="flex h-12 w-12 -translate-y-3 items-center justify-center rounded-full bg-primary-500 text-text-inverse shadow-glow-primary transition-transform duration-200 active:scale-95">
            <Icon name="plus" className="h-6 w-6" strokeWidth={2.5} />
          </span>
          <span className="-mt-2.5">Créer</span>
        </NavLink>
        <NavCell item={AUTH_ITEMS[2]} badge={unread} />
        <NavCell item={AUTH_ITEMS[3]} />
      </div>
    </nav>
  )
}

/** Espace réservé sous le contenu mobile (compenser la barre fixe). */
export function MobileBottomNavSpacer() {
  return <div aria-hidden="true" className="h-16 shrink-0 md:hidden" />
}
