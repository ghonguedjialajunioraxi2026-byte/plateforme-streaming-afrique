import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { cn } from '../../utils/cn'
import { Avatar, Button } from '../ui'
import { Icon } from '../ui/icons'
import { ChatPanel } from '../streaming'
import { FlickLogo } from '../branding'
import { useAuth } from '../../features/auth/useAuth'
import { regionApi, type EmergingCreator } from '../../services/api/region'

const RAIL_KEY = 'flick-rail-collapsed'

function initialCollapsed(): boolean {
  try {
    return window.localStorage.getItem(RAIL_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * Colonne droite de l'accueil : chat du stream,
 * chaînes recommandées et bloc communauté. Réductible — le contenu
 * principal récupère l'espace libéré. Affichée sur grand écran.
 */
export function HomeRightRail() {
  const [collapsed, setCollapsed] = useState<boolean>(initialCollapsed)
  const { isAuthenticated } = useAuth()
  const [recommended, setRecommended] = useState<EmergingCreator[]>([])

  // Chaînes recommandées RÉELLES (créateurs émergents, jamais de mocks).
  useEffect(() => {
    let cancelled = false
    regionApi
      .emerging({ size: 5 })
      .then((creators) => {
        if (!cancelled) setRecommended(creators)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
    }
  }, [])

  function toggleCollapsed() {
    setCollapsed((previous) => {
      try {
        window.localStorage.setItem(RAIL_KEY, previous ? '0' : '1')
      } catch {
        // Stockage indisponible : le rail reste fonctionnel.
      }
      return !previous
    })
  }

  if (collapsed) {
    return (
      <aside
        aria-label="Colonne latérale réduite"
        className="sticky top-12 hidden h-[calc(100vh-3rem)] w-10 shrink-0 items-start justify-center border-l border-border-default bg-bg-secondary pt-3 xl:flex"
      >
        <button
          type="button"
          onClick={toggleCollapsed}
          aria-label="Afficher le chat et les recommandations"
          aria-expanded="false"
          title="Afficher le panneau latéral"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary"
        >
          <Icon name="chat" className="h-4 w-4" />
        </button>
      </aside>
    )
  }

  return (
    <aside
      aria-label="Chat et recommandations"
      className="sticky top-12 hidden h-[calc(100vh-3rem)] w-[270px] shrink-0 flex-col overflow-y-auto border-l border-border-default bg-bg-secondary xl:flex"
    >
      {/* Header chat */}
      <div className="flex items-center justify-between px-3 py-2.5">
        <h2 className="font-display text-sm font-semibold text-text-primary">
          Chat du stream
        </h2>
        <button
          type="button"
          onClick={toggleCollapsed}
          aria-label="Réduire le panneau latéral"
          aria-expanded="true"
          title="Réduire"
          className="flex h-7 w-7 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary"
        >
          <Icon name="chevron-down" className="h-4 w-4 -rotate-90" />
        </button>
      </div>

      {/* Messages */}
      <div className="flex min-h-64 flex-1 flex-col border-t border-border-default">
        <ChatPanel bare messages={[]} isConnected={false} className="flex-1" />
        <p className="px-3 pb-2 text-xs text-text-muted">
          Ouvre un live pour rejoindre son chat en direct.
        </p>
      </div>

      {/* Chaînes recommandées */}
      <div className="border-t border-border-default px-3 py-2.5">
        <div className="flex items-center justify-between">
          <p className="text-xs font-semibold text-text-secondary">
            Chaînes recommandées
          </p>
          <Link
            to="/explorer"
            className="text-xs font-medium text-primary-400 transition-colors hover:text-primary-300"
          >
            Voir tout →
          </Link>
        </div>
        {recommended.length === 0 ? (
          <p className="mt-2 text-xs text-text-muted">
            Aucune recommandation pour le moment.
          </p>
        ) : (
        <ul className="mt-2 space-y-1">
          {recommended.map((channel) => (
            <li
              key={channel.id}
              className="flex items-center gap-2 rounded-lg px-1 py-1 transition-colors hover:bg-bg-hover"
            >
              <Avatar name={channel.displayName} size="sm" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-text-primary">
                  {channel.displayName}
                </span>
                <span className="block truncate text-xs text-text-muted">
                  {channel.followerCount} abonnés
                </span>
              </span>
              <Link
                to={`/channel/${channel.slug}`}
                title={`Voir la chaîne ${channel.displayName} pour la suivre`}
                className="flex h-7 shrink-0 items-center rounded-md bg-primary-500 px-2.5 text-xs font-semibold text-text-inverse transition-colors hover:bg-primary-400"
              >
                Suivre
              </Link>
            </li>
          ))}
        </ul>
        )}
      </div>

      {/* Bloc communauté */}
      {!isAuthenticated && (
        <div className="border-t border-border-default p-3">
          <div className={cn('rounded-lg border border-border-default bg-bg-surface p-3')}>
            <div className="flex items-center gap-2">
              <FlickLogo variant="mark" size="sm" link={false} />
              <p className="text-sm font-semibold text-text-primary">
                Rejoins la communauté FLICK
              </p>
            </div>
            <p className="mt-1.5 text-xs text-text-muted">
              Découvre les meilleurs créateurs. Une seule plateforme.
            </p>
            <Link to="/inscription" className="mt-2.5 block">
              <Button size="sm" fullWidth>
                S&apos;inscrire
              </Button>
            </Link>
          </div>
        </div>
      )}
    </aside>
  )
}
