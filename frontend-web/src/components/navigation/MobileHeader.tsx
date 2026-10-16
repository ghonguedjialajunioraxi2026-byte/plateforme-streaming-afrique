import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { FlickLogo } from '../branding'
import { Avatar } from '../ui'
import { BottomSheet } from '../ui/BottomSheet'
import { Icon, type IconName } from '../ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { ThemeToggle } from '../../features/theme/ThemeToggle'
import { useUnreadCount } from '../../hooks/useUnreadCount'
import { cn } from '../../utils/cn'

interface MenuEntry {
  to: string
  label: string
  icon: IconName
  group: 'account' | 'spaces'
}

const ACCOUNT_ENTRIES: MenuEntry[] = [
  { to: '/profile', label: 'Mon profil', icon: 'user', group: 'account' },
  { to: '/following', label: 'Chaînes suivies', icon: 'heart', group: 'account' },
  { to: '/wallet', label: 'Portefeuille', icon: 'wallet', group: 'account' },
  { to: '/subscriptions', label: 'Abonnements', icon: 'star', group: 'account' },
  { to: '/settings', label: 'Paramètres', icon: 'settings', group: 'account' },
]

function entriesForRole(role: string | undefined): MenuEntry[] {
  const entries = [...ACCOUNT_ENTRIES]
  if (role === 'CREATOR' || role === 'MODERATOR' || role === 'ADMIN' || role === 'SUPER_ADMIN') {
    entries.push({ to: '/creator', label: 'Creator Studio', icon: 'play', group: 'spaces' })
  }
  if (role === 'MODERATOR' || role === 'ADMIN' || role === 'SUPER_ADMIN') {
    entries.push({ to: '/moderation', label: 'Modération', icon: 'shield', group: 'spaces' })
  }
  if (role === 'ADMIN' || role === 'SUPER_ADMIN') {
    entries.push({ to: '/admin', label: 'Administration', icon: 'settings', group: 'spaces' })
  }
  return entries
}

/** Écrans « maison » : logo FLICK à gauche (maquette Accueil/Explorer). */
const HOME_PATHS = new Set(['/', '/home', '/explorer', '/following'])

/** Écrans d'authentification : retour + logo centré, sans actions. */
const AUTH_PATHS = new Set(['/connexion', '/inscription'])

const EXACT_TITLES: Record<string, string> = {
  '/recherche': 'Rechercher',
  '/categories': 'Catégories',
  '/notifications': 'Notifications',
  '/messages': 'Messages',
  '/profile': 'Profil',
  '/settings': 'Paramètres',
  '/wallet': 'Mon portefeuille',
  '/wallet/acheter': 'Acheter des coins',
  '/wallet/transactions': 'Historique',
  '/subscriptions': 'Abonnements',
  '/assistant': 'Assistant',
  '/mot-de-passe-oublie': 'Mot de passe oublié',
  '/reinitialisation': 'Réinitialisation',
  '/verification-email': 'Vérification',
}

/** Titre de l'écran interne selon la route (maquette : retour + titre). */
export function mobileTitleFor(pathname: string): string {
  if (EXACT_TITLES[pathname] !== undefined) return EXACT_TITLES[pathname]
  if (pathname.startsWith('/live/')) return pathname.endsWith('/chat') ? 'Chat du stream' : 'En direct'
  if (pathname.startsWith('/video/')) return 'Vidéo'
  if (pathname.startsWith('/clip/') || pathname.startsWith('/c/')) return 'Clip'
  if (pathname.startsWith('/channel/')) return 'Chaîne'
  if (pathname.startsWith('/wallet/paiement')) return 'Paiement'
  if (pathname.startsWith('/wallet')) return 'Mon portefeuille'
  if (pathname.startsWith('/creator')) return 'Creator Studio'
  if (pathname.startsWith('/moderation')) return 'Modération'
  if (pathname.startsWith('/admin')) return 'Administration'
  return 'FLICK'
}

/**
 * En-tête mobile FLICK — 3 variantes fidèles à la maquette :
 * - `home` (Accueil/Explorer/…) : logo officiel + recherche, cloche,
 *   avatar / connexion ;
 * - `sub` (écrans internes) : retour + titre + mêmes actions ;
 * - `auth` (connexion/inscription) : retour + logo centré.
 * Compact et tactile (h-14, sticky, safe-area). Visible sous `md`
 * uniquement — le desktop garde `Navbar`.
 */
export function MobileHeader() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const { user, isAuthenticated, logout } = useAuth()
  const unread = useUnreadCount()
  const [menuOpen, setMenuOpen] = useState(false)
  const username = user?.username ?? 'Utilisateur'

  async function handleLogout() {
    setMenuOpen(false)
    await logout()
    navigate('/connexion', { replace: true })
  }

  function goBack() {
    if (window.history.length > 1) {
      navigate(-1)
    } else {
      navigate('/', { replace: true })
    }
  }

  const searchButton = (
    <Link
      to="/recherche"
      aria-label="Rechercher"
      className="flex h-11 w-11 items-center justify-center rounded-lg text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
    >
      <Icon name="search" className="h-5 w-5" />
    </Link>
  )

  const notificationsButton = isAuthenticated ? (
    <Link
      to="/notifications"
      aria-label={unread > 0 ? `Notifications, ${unread} non lues` : 'Notifications'}
      className="relative flex h-11 w-11 items-center justify-center rounded-lg text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
    >
      <Icon name="bell" className="h-5 w-5" />
      {unread > 0 && (
        <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
          {unread > 9 ? '9+' : unread}
        </span>
      )}
    </Link>
  ) : null

  const accountControl = isAuthenticated ? (
    <button
      type="button"
      onClick={() => setMenuOpen(true)}
      aria-label="Ouvrir le menu du compte"
      aria-haspopup="dialog"
      className="flex h-11 w-11 items-center justify-center rounded-lg transition-colors active:bg-bg-hover"
    >
      <Avatar name={username} size="sm" />
    </button>
  ) : (
    <Link
      to="/connexion"
      className="flex h-10 items-center rounded-lg bg-primary-500 px-4 text-sm font-semibold text-text-inverse transition-colors active:bg-primary-400"
    >
      Connexion
    </Link>
  )

  // Variante auth : retour + logo centré (maquette connexion/inscription).
  if (AUTH_PATHS.has(pathname)) {
    return (
      <header className="sticky top-0 z-30 flex h-14 items-center gap-1 border-b border-border-default bg-bg-secondary/95 px-3 backdrop-blur-md md:hidden">
        <button
          type="button"
          onClick={goBack}
          aria-label="Retour"
          className="flex h-11 w-11 items-center justify-center rounded-lg text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
        >
          <Icon name="arrow-left" className="h-5 w-5" />
        </button>
        <span className="flex flex-1 items-center justify-center">
          <FlickLogo size="sm" />
        </span>
        <span aria-hidden="true" className="w-11" />
      </header>
    )
  }

  // Variante écran interne : retour + titre + actions.
  if (!HOME_PATHS.has(pathname)) {
    return (
      <>
        <header className="sticky top-0 z-30 flex h-14 items-center gap-1 border-b border-border-default bg-bg-secondary/95 px-3 backdrop-blur-md md:hidden">
          <button
            type="button"
            onClick={goBack}
            aria-label="Retour"
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
          >
            <Icon name="arrow-left" className="h-5 w-5" />
          </button>
          <h1 className="min-w-0 flex-1 truncate font-display text-lg font-bold tracking-tight text-text-primary">
            {mobileTitleFor(pathname)}
          </h1>
          {searchButton}
          <ThemeToggle size="lg" />
          {notificationsButton}
          {isAuthenticated ? (
            accountControl
          ) : (
            <Link
              to="/connexion"
              aria-label="Connexion"
              className="flex h-11 w-11 items-center justify-center rounded-lg text-text-secondary transition-colors active:bg-bg-hover active:text-text-primary"
            >
              <Icon name="user" className="h-5 w-5" />
            </Link>
          )}
        </header>
        <AccountSheet
          open={menuOpen}
          username={username}
          role={user?.role}
          onClose={() => setMenuOpen(false)}
          onLogout={() => void handleLogout()}
        />
      </>
    )
  }

  // Variante home : logo + actions (maquette Accueil/Explorer).
  return (
    <>
      <header className="sticky top-0 z-30 flex h-14 items-center gap-1 border-b border-border-default bg-bg-secondary/95 px-3 backdrop-blur-md md:hidden">
        <FlickLogo size="sm" className="mr-auto" />
        {searchButton}
        <ThemeToggle size="lg" />
        {notificationsButton}
        {accountControl}
      </header>

      <AccountSheet
        open={menuOpen}
        username={username}
        role={user?.role}
        onClose={() => setMenuOpen(false)}
        onLogout={() => void handleLogout()}
      />
    </>
  )
}

function AccountSheet({
  open,
  username,
  role,
  onClose,
  onLogout,
}: {
  open: boolean
  username: string
  role: string | undefined
  onClose: () => void
  onLogout: () => void
}) {
  const entries = entriesForRole(role)
  return (
    <BottomSheet open={open} onClose={onClose} title={username}>
      {/* En-tête profil maquette (écran 24) */}
      <Link
        to="/profile"
        onClick={onClose}
        className="flex items-center gap-3 rounded-xl px-2 py-2 transition-colors active:bg-bg-hover"
        aria-label="Voir mon profil"
      >
        <Avatar name={username} size="md" />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-[15px] font-bold text-text-primary">
            {username}
          </span>
          <span className="block truncate text-[13px] text-text-muted">@{username}</span>
        </span>
        <Icon name="chevron-down" className="h-4 w-4 -rotate-90 text-text-muted" />
      </Link>
      <nav aria-label="Menu du compte" className="space-y-4 pb-2">
        <MenuGroup
          title="Compte"
          entries={entries.filter((entry) => entry.group === 'account')}
          onNavigate={onClose}
        />
        {entries.some((entry) => entry.group === 'spaces') && (
          <MenuGroup
            title="Espaces"
            entries={entries.filter((entry) => entry.group === 'spaces')}
            onNavigate={onClose}
          />
        )}
        <button
          type="button"
          onClick={onLogout}
          className="flex min-h-11 w-full items-center gap-3 rounded-lg px-2 py-2 text-[15px] font-medium text-danger transition-colors active:bg-bg-hover"
        >
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-bg-elevated">
            <Icon name="logout" className="h-5 w-5" />
          </span>
          Déconnexion
        </button>
      </nav>
    </BottomSheet>
  )
}

function MenuGroup({
  title,
  entries,
  onNavigate,
}: {
  title: string
  entries: MenuEntry[]
  onNavigate: () => void
}) {
  return (
    <section aria-label={title}>
      <h3 className="px-2 text-xs font-bold tracking-wider text-text-muted uppercase">
        {title}
      </h3>
      <ul className="mt-1 divide-y divide-border-default">
        {entries.map((entry) => (
          <li key={entry.to}>
            <Link
              to={entry.to}
              onClick={onNavigate}
              className={cn(
                'flex min-h-11 items-center gap-3 rounded-lg px-2 py-2 text-[15px] font-medium text-text-primary',
                'transition-colors active:bg-bg-hover',
              )}
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-bg-elevated text-text-secondary">
                <Icon name={entry.icon} className="h-5 w-5" />
              </span>
              {entry.label}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
