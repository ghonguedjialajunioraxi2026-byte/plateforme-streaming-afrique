import { NavLink, Link, useNavigate } from 'react-router'
import { Bell, ChatCircleDots, Plus, CaretDown } from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import { FlickLogo } from '../branding'
import { Avatar, Dropdown, DropdownItem, DropdownSeparator } from '../ui'
import { Icon, type IconName } from '../ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { ThemeToggle } from '../../features/theme/ThemeToggle'
import { useUnreadCount } from '../../hooks/useUnreadCount'
import { SearchBar } from './SearchBar'

interface NavbarProps {
  /** Nombre de notifications non lues (notifications : étape ultérieure) */
  notificationCount?: number
}

interface NavLinkDef {
  to: string
  label: string
  icon: IconName
  /** Actif uniquement pour le chemin exact (nécessaire pour '/') */
  end?: boolean
}

const navLinks: NavLinkDef[] = [
  { to: '/', label: 'Accueil', icon: 'home', end: true },
  { to: '/explorer', label: 'Explorer', icon: 'compass' },
  { to: '/categories', label: 'Catégories', icon: 'grid' },
]

export function Navbar({ notificationCount = 0 }: NavbarProps) {
  const navigate = useNavigate()
  const { user, isAuthenticated, logout } = useAuth()
  const username = user?.username ?? 'Utilisateur'

  // Compteur temps réel de la cloche (60s) partagé avec le header mobile.
  const liveCount = useUnreadCount(notificationCount)

  async function handleLogout() {
    await logout()
    navigate('/connexion', { replace: true })
  }

  return (
    <header className="sticky top-0 z-30 hidden h-16 items-center gap-4 border-b border-border-default bg-bg-secondary/95 px-4 backdrop-blur-md md:flex lg:gap-4">
      {/* Logo officiel FLICK (source unique : assets/branding) */}
      <FlickLogo className="shrink-0" />

      {/* Liens de navigation */}
      <nav aria-label="Navigation principale" className="hidden shrink-0 items-center gap-0.5 md:flex">
        {navLinks.map((link) => (
          <NavLink
            key={link.to}
            to={link.to}
            end={link.end}
            className={({ isActive }) =>
              cn(
                'relative flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold transition-colors',
                isActive
                  ? 'text-text-primary'
                  : 'text-text-secondary hover:text-text-primary',
              )
            }
          >
            {({ isActive }) => (
              <>
                <Icon
                  name={link.icon}
                  className={cn('h-[18px] w-[18px]', isActive ? 'text-primary-400' : 'text-text-muted')}
                />
                <span>{link.label}</span>
                {isActive && (
                  <span
                    aria-hidden="true"
                    className="absolute inset-x-3 -bottom-[17px] h-0.5 rounded-full bg-primary-500 shadow-glow-primary"
                  />
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Recherche avec suggestions temps réel */}
      <SearchBar />

      {/* Actions droite */}
      <div className="flex shrink-0 items-center gap-1.5">
        {isAuthenticated ? (
          <>
            {/* Créer : studio / lancement de live */}
            <Link
              to="/creator/stream"
              className="hidden h-9 items-center gap-1.5 rounded-lg bg-primary-500 px-3.5 text-sm font-bold text-text-inverse shadow-glow-primary transition-all hover:bg-primary-400 lg:flex"
            >
              <Plus size={16} weight="bold" />
              Créer
            </Link>

            {/* Notifications */}
            <Link
              to="/notifications"
              aria-label={`Notifications${liveCount > 0 ? ` · ${liveCount} non lues` : ''}`}
              className="relative flex h-10 w-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              <Bell size={20} />
              {liveCount > 0 && (
                <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
                  {liveCount > 9 ? '9+' : liveCount}
                </span>
              )}
            </Link>

            {/* Messages */}
            <Link
              to="/messages"
              aria-label="Messages"
              className="flex h-10 w-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              <ChatCircleDots size={20} />
            </Link>

            {/* Sombre / clair */}
            <ThemeToggle />

            {/* Menu utilisateur */}
            <div className="ml-1 flex items-center gap-1">
              <span className="hidden max-w-32 truncate text-sm font-medium text-text-primary lg:block">
                {username}
              </span>
              <Dropdown
                menuClassName="w-56"
                trigger={
                  <>
                    <Avatar name={username} size="sm" />
                    <CaretDown size={14} className="ml-0.5 text-text-muted" />
                  </>
                }
              >
                <DropdownItem to="/profile" icon={<Icon name="user" className="h-4 w-4" />}>
                  Mon profil
                </DropdownItem>
                <DropdownItem to="/creator" icon={<Icon name="play" className="h-4 w-4" />}>
                  Creator Studio
                </DropdownItem>
                <DropdownItem to="/following" icon={<Icon name="heart" className="h-4 w-4" />}>
                  Chaînes suivies
                </DropdownItem>
                <DropdownItem to="/wallet" icon={<Icon name="wallet" className="h-4 w-4" />}>
                  Portefeuille
                </DropdownItem>
                <DropdownItem to="/settings" icon={<Icon name="settings" className="h-4 w-4" />}>
                  Paramètres
                </DropdownItem>
                <DropdownSeparator />
                <DropdownItem
                  icon={<Icon name="logout" className="h-4 w-4" />}
                  danger
                  onClick={() => {
                    void handleLogout()
                  }}
                >
                  Déconnexion
                </DropdownItem>
              </Dropdown>
            </div>
          </>
        ) : (
          <>
            {/* Sombre / clair */}
            <ThemeToggle />
            <Link
              to="/inscription"
              className="flex h-9 items-center rounded-lg px-3 text-sm font-medium text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              Créer un compte
            </Link>
            <Link
              to="/connexion"
              className="flex h-9 items-center rounded-lg bg-primary-500 px-4 text-sm font-semibold text-text-inverse transition-colors hover:bg-primary-600"
            >
              Connexion
            </Link>
          </>
        )}
      </div>
    </header>
  )
}