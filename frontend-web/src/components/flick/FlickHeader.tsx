import { useState } from 'react'
import { Link, NavLink, useNavigate } from 'react-router'
import {
  Bell,
  ChatCircleDots,
  MagnifyingGlass,
  Plus,
  CaretDown,
  House,
  Compass,
  SquaresFour,
} from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import { useAuth } from '../../features/auth/useAuth'
import { ThemeToggle } from '../../features/theme/ThemeToggle'
import { FlickAvatar } from './FlickAvatar'

const NAV = [
  { to: '/', label: 'Accueil', icon: House, end: true },
  { to: '/explorer', label: 'Explorer', icon: Compass, end: false },
  { to: '/categories', label: 'Catégories', icon: SquaresFour, end: false },
]

/**
 * Header supérieur fixe FLICK — H: 64px.
 * Logo gradient cyan/bleu + nav avec indication cyan + recherche
 * centrale 400-520px + Créer + notifications + messagerie + avatar.
 */
export function FlickHeader() {
  const navigate = useNavigate()
  const { user, isAuthenticated, logout } = useAuth()
  const [query, setQuery] = useState('')
  const [menuOpen, setMenuOpen] = useState(false)
  const username = user?.username ?? 'Invité'

  function submitSearch(event: React.FormEvent) {
    event.preventDefault()
    if (query.trim().length === 0) return
    navigate(`/recherche?q=${encodeURIComponent(query.trim())}`)
  }

  async function handleLogout() {
    setMenuOpen(false)
    await logout()
    navigate('/connexion', { replace: true })
  }

  return (
    <header className="sticky top-0 z-40 hidden h-16 items-center gap-4 border-b border-border-default bg-bg-secondary/95 px-4 backdrop-blur-md md:flex">
      {/* Logo */}
      <Link to="/" className="flex shrink-0 items-center gap-2" aria-label="FLICK — Accueil">
        <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-[#00F0FF] to-[#2563EB] text-lg font-black text-black shadow-[0_0_20px_rgb(0_240_255/0.35)]">
          F
        </span>
        <span className="font-display text-xl font-black tracking-tight text-text-primary">
          FLICK
        </span>
      </Link>

      {/* Navigation principale */}
      <nav aria-label="Navigation principale" className="flex shrink-0 items-center gap-1">
        {NAV.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn(
                'relative flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold transition-colors',
                isActive ? 'text-text-primary' : 'text-text-secondary hover:text-text-primary',
              )
            }
          >
            {({ isActive }) => (
              <>
                <item.icon size={18} weight={isActive ? 'fill' : 'regular'} className={isActive ? 'text-primary-400' : undefined} />
                <span>{item.label}</span>
                {isActive && (
                  <span className="absolute inset-x-3 -bottom-[13px] h-0.5 rounded-full bg-primary-500 shadow-glow-primary" />
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Recherche centrale */}
      <form onSubmit={submitSearch} className="mx-auto w-full max-w-[520px] min-w-[280px] flex-1" role="search">
        <div className="relative">
          <MagnifyingGlass size={18} className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-text-muted" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher un créateur, un jeu, une catégorie..."
            aria-label="Rechercher sur FLICK"
            className="h-10 w-full rounded-full border border-border-default bg-bg-surface pl-10 pr-4 text-sm text-text-primary placeholder:text-text-muted transition-colors focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
          />
        </div>
      </form>

      {/* Actions droite */}
      <div className="flex shrink-0 items-center gap-1.5">
        {isAuthenticated ? (
          <>
            <Link
              to="/creator/stream"
              className="flex h-9 items-center gap-1.5 rounded-lg bg-primary-500 px-3.5 text-sm font-bold text-black shadow-glow-primary transition-all hover:bg-primary-300"
            >
              <Plus size={16} weight="bold" />
              Créer
            </Link>
            <Link
              to="/notifications"
              aria-label="Notifications"
              className="relative flex h-10 w-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              <Bell size={20} />
              <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-bold text-white">
                3
              </span>
            </Link>
            <Link
              to="/messages"
              aria-label="Messagerie"
              className="flex h-10 w-10 items-center justify-center rounded-lg text-text-secondary transition-colors hover:bg-bg-hover hover:text-text-primary"
            >
              <ChatCircleDots size={20} />
            </Link>
            {/* Sombre / clair */}
            <ThemeToggle />
            <div className="relative ml-1">
              <button
                type="button"
                onClick={() => setMenuOpen((v) => !v)}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                className="flex items-center gap-1.5 rounded-lg p-1 transition-colors hover:bg-bg-hover"
              >
                <FlickAvatar name={username} size="sm" />
                <CaretDown size={14} className="text-text-muted" />
              </button>
              {menuOpen && (
                <>
                  <button type="button" aria-label="Fermer le menu" onClick={() => setMenuOpen(false)} className="fixed inset-0 z-10 cursor-default" />
                  <div role="menu" className="absolute right-0 z-20 mt-2 w-56 overflow-hidden rounded-xl border border-border-default bg-bg-surface p-1.5 shadow-2xl">
                    <p className="truncate px-3 py-2 text-sm font-semibold text-text-primary">{username}</p>
                    <Link to="/profile" onClick={() => setMenuOpen(false)} role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-text-secondary hover:bg-bg-hover hover:text-text-primary">Mon profil</Link>
                    <Link to="/creator" onClick={() => setMenuOpen(false)} role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-text-secondary hover:bg-bg-hover hover:text-text-primary">Studio Créateur</Link>
                    <Link to="/following" onClick={() => setMenuOpen(false)} role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-text-secondary hover:bg-bg-hover hover:text-text-primary">Chaînes suivies</Link>
                    <Link to="/wallet" onClick={() => setMenuOpen(false)} role="menuitem" className="block rounded-lg px-3 py-2 text-sm text-text-secondary hover:bg-bg-hover hover:text-text-primary">Portefeuille</Link>
                    <button type="button" onClick={() => void handleLogout()} role="menuitem" className="block w-full rounded-lg px-3 py-2 text-left text-sm text-danger hover:bg-danger/10">Déconnexion</button>
                  </div>
                </>
              )}
            </div>
          </>
        ) : (
          <>
            <ThemeToggle />
            <Link to="/inscription" className="flex h-10 items-center rounded-lg px-3 text-sm font-medium text-text-secondary hover:text-text-primary">
              Créer un compte
            </Link>
            <Link to="/connexion" className="flex h-10 items-center rounded-lg bg-primary-500 px-4 text-sm font-bold text-black hover:bg-primary-300">
              Connexion
            </Link>
          </>
        )}
      </div>
    </header>
  )
}
