import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { cn } from '../../utils/cn'
import { APP_NAME } from '../../constants/app'
import { SIDEBAR_CATEGORIES, type FollowedChannel } from '../../constants/discover'
import type { NavItem } from '../../types/navigation'
import { useI18n, type I18nKey } from '../../i18n'
import { FlickLogo } from '../branding'
import { Avatar } from '../ui'
import { Icon } from '../ui/icons'
import type { IconName } from '../ui/icons'
import { formatCount } from '../../utils/format'
import { categoriesApi } from '../../services/api/categories'

interface SidebarProps {
  items: NavItem[]
  /** Libellé de marque du header de la sidebar (ex: "Creator Studio") */
  brandLabel?: string
  /**
   * Affiche l'en-tête marque (symbole + nom) en haut de la sidebar.
   * À `false` quand une Navbar affiche déjà le logo FLICK au-dessus
   * (PublicLayout / UserLayout) : évite le doublon de logo.
   * Reste à `true` pour les dashboards (Sidebar seule, sans Navbar).
   * @default true
   */
  showBrandHeader?: boolean
  /** Classes de positionnement passées par le parent (sticky, hauteur…) */
  className?: string
  /** Chaînes suivies — affiche la section « Pour vous » quand fournie */
  followed?: FollowedChannel[]
  /** Affiche la section « Catégories » */
  showCategories?: boolean
  /**
   * Masque la sidebar sous `md` (mobile plein écran façon maquette :
   * header + bottom nav couvrent déjà la navigation).
   * Dashboards : rester à `false` (pas de bottom nav là-bas).
   * @default false
   */
  hideOnMobile?: boolean
}

const COLLAPSE_KEY = 'flick-sidebar-collapsed'

function initialCollapsed(): boolean {
  try {
    return window.localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return false
  }
}

export function Sidebar({ items, brandLabel, showBrandHeader = true, className, followed, showCategories = false, hideOnMobile = false }: SidebarProps) {
  const location = useLocation()
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState<boolean>(initialCollapsed)
  // Catégories RÉELLES (`GET /categories`) ; repli = noms statiques sans compteurs.
  const [realCategories, setRealCategories] = useState<Array<{ name: string; slug: string }> | null>(null)

  useEffect(() => {
    if (!showCategories) return
    let cancelled = false
    categoriesApi
      .list(undefined, 0, 7)
      .then((page) => {
        if (!cancelled) setRealCategories(page.content.map((c) => ({ name: c.name, slug: c.slug })))
      })
      .catch(() => {
        if (!cancelled) setRealCategories([])
      })
    return () => {
      cancelled = true
    }
  }, [showCategories])

  function iconFor(name: string): IconName {
    const match = SIDEBAR_CATEGORIES.find((c) => c.name.toLowerCase() === name.toLowerCase())
    return match?.icon ?? 'grid'
  }

  function toggleCollapsed() {
    setCollapsed((previous) => {
      try {
        window.localStorage.setItem(COLLAPSE_KEY, previous ? '0' : '1')
      } catch {
        // Stockage indisponible : la sidebar reste fonctionnelle.
      }
      return !previous
    })
  }

  return (
    <aside
      aria-label="Navigation latérale"
      className={cn(
        'shrink-0 flex-col border-r border-border-default bg-bg-secondary transition-[width] duration-150',
        hideOnMobile ? 'hidden md:flex' : 'flex',
        collapsed ? 'w-[72px]' : 'w-[72px] md:w-60',
        className,
      )}
    >
      {/* En-tête marque FLICK — uniquement quand aucune Navbar ne l'affiche déjà.
          (PublicLayout/UserLayout passent showBrandHeader={false} pour éviter le doublon.) */}
      {showBrandHeader ? (
        <div className="flex h-12 items-center justify-center gap-2 border-b border-border-default px-2 md:justify-start md:px-3">
          <FlickLogo variant="mark" size="sm" link={false} />
          {!collapsed && (
            <span className="hidden truncate font-display text-base font-bold text-text-primary md:inline">
              {brandLabel ?? APP_NAME}
            </span>
          )}
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? 'Déplier la sidebar' : 'Réduire la sidebar'}
            aria-expanded={!collapsed}
            title={collapsed ? 'Déplier' : 'Réduire'}
            className="ml-auto hidden h-7 w-7 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary md:flex"
          >
            <Icon
              name="chevron-down"
              className={cn('h-4 w-4 transition-transform', collapsed ? 'rotate-90' : '-rotate-90')}
            />
          </button>
        </div>
      ) : (
        /* Sans header marque : bouton replier/plier compact, sans h-12 ni bordure,
           pour ne laisser aucun espace vide à la place du logo supprimé. */
        <div className="hidden justify-end px-2 pt-2 md:flex">
          <button
            type="button"
            onClick={toggleCollapsed}
            aria-label={collapsed ? 'Déplier la sidebar' : 'Réduire la sidebar'}
            aria-expanded={!collapsed}
            title={collapsed ? 'Déplier' : 'Réduire'}
            className="flex h-7 w-7 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary"
          >
            <Icon
              name="chevron-down"
              className={cn('h-4 w-4 transition-transform', collapsed ? 'rotate-90' : '-rotate-90')}
            />
          </button>
        </div>
      )}

      {/* Navigation */}
      <nav aria-label="Sections" className="flex-1 space-y-1 overflow-y-auto px-2 py-3">
        {items.map((item) => {
          const isActive =
            item.to === '/'
              ? location.pathname === '/'
              : location.pathname.startsWith(item.to)
          const label = item.labelKey ? t(item.labelKey as I18nKey) : item.label

          return (
            <Link
              key={item.to}
              to={item.to}
              title={label}
              aria-current={isActive ? 'page' : undefined}
              className={cn(
                'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                collapsed && 'justify-center px-0',
                isActive
                  ? 'bg-primary-500/10 text-primary-400'
                  : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
              )}
            >
              <Icon name={item.icon} className="h-5 w-5 shrink-0" />
              {!collapsed && <span className="hidden truncate md:inline">{label}</span>}
            </Link>
          )
        })}

        {/* Chaînes suivies */}
        {followed && followed.length > 0 && (
          <div className="pt-4">
            {!collapsed && (
              <p className="hidden px-3 pb-2 text-xs font-semibold tracking-wide text-text-muted md:block">
                POUR VOUS
              </p>
            )}
            <ul className="space-y-0.5">
              {followed.map((channel) => (
                <li key={channel.id}>
                  <Link
                    to={`/channel/${channel.slug}`}
                    title={channel.name}
                    className={cn(
                      'flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition-colors hover:bg-bg-hover',
                      collapsed && 'justify-center px-0',
                    )}
                  >
                    <span className="relative shrink-0">
                      <Avatar name={channel.name} size="sm" />
                      {channel.isLive && (
                        <span
                          aria-label="En direct"
                          className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-bg-secondary bg-live"
                        />
                      )}
                    </span>
                    {!collapsed && (
                      <span className="hidden min-w-0 flex-1 md:block">
                        <span className="block truncate text-sm font-medium text-text-primary">
                          {channel.name}
                        </span>
                        <span className="block truncate text-xs text-text-muted">
                          {channel.category}
                          {channel.isLive && (
                            <> · <span className="text-live">{formatCount(channel.viewers)}</span></>
                          )}
                        </span>
                      </span>
                    )}
                  </Link>
                </li>
              ))}
            </ul>
            {!collapsed && (
              <Link
                to="/following"
                className="mt-1 hidden rounded-lg px-3 py-1.5 text-xs font-medium text-text-muted transition-colors hover:text-primary-400 md:block"
              >
                Afficher plus →
              </Link>
            )}
          </div>
        )}

        {/* Catégories (réelles ; compteurs jamais inventés) */}
        {showCategories && (
          <div className="pt-4">
            {!collapsed && (
              <p className="hidden px-3 pb-2 text-xs font-semibold tracking-wide text-text-muted md:block">
                CATÉGORIES
              </p>
            )}
            <ul className="space-y-0.5">
              {(realCategories ?? []).map((category) => (
                <li key={category.slug}>
                  <Link
                    to={`/categories?search=${encodeURIComponent(category.name)}`}
                    title={category.name}
                    className={cn(
                      'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors hover:bg-bg-hover',
                      collapsed && 'justify-center px-0',
                    )}
                  >
                    <Icon name={iconFor(category.name)} className="h-4 w-4 shrink-0 text-text-muted" />
                    {!collapsed && (
                      <span className="hidden min-w-0 flex-1 truncate text-text-secondary md:inline">
                        {category.name}
                      </span>
                    )}
                  </Link>
                </li>
              ))}
              {realCategories === null && (
                <li aria-hidden="true" className="px-3 py-1 text-xs text-text-muted">
                  …
                </li>
              )}
            </ul>
          </div>
        )}

        {/* Bloc créateur */}
        {!collapsed && (
          <div className="mt-4 hidden rounded-lg border border-border-default bg-bg-surface p-3 md:block">
            <div className="flex items-center gap-2">
              <FlickLogo variant="mark" size="sm" link={false} />
              <p className="text-sm font-semibold text-text-primary">
                Deviens créateur sur {APP_NAME}
              </p>
            </div>
            <p className="mt-1.5 text-xs text-text-muted">
              Partage ta passion avec le monde
            </p>
            <Link
              to="/inscription"
              className="mt-2.5 flex h-8 items-center justify-center rounded-lg bg-primary-500 text-sm font-semibold text-text-inverse transition-colors hover:bg-primary-400"
            >
              Commencer
            </Link>
          </div>
        )}
      </nav>
    </aside>
  )
}
