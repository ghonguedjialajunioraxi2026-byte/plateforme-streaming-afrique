import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import {
  GameController,
  MusicNote,
  Trophy,
  ChatCircleDots,
  Microphone,
  GraduationCap,
  Cpu,
  CaretLeft,
  CaretRight,
} from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import { useFollowedChannels } from '../../features/follow/useFollowedChannels'
import { categoriesApi } from '../../services/api/categories'
import { FlickAvatar } from './FlickAvatar'
import { FlickLiveBadge } from './FlickLiveBadge'

const CATEGORY_ICONS = [
  { name: 'Gaming', Icon: GameController },
  { name: 'Musique', Icon: MusicNote },
  { name: 'Sport', Icon: Trophy },
  { name: 'Discussion', Icon: ChatCircleDots },
  { name: 'Podcast', Icon: Microphone },
  { name: 'Éducation', Icon: GraduationCap },
  { name: 'Technologie', Icon: Cpu },
]

/**
 * Sidebar gauche FLICK — 240px, pliable.
 * Chaînes suivies RÉELLES (connecté) + catégories RÉELLES
 * (compteurs jamais inventés), widget créateur.
 */
export function FlickSidebar() {
  const [collapsed, setCollapsed] = useState(false)
  const followed = useFollowedChannels()
  const [categories, setCategories] = useState<string[] | null>(null)

  useEffect(() => {
    let cancelled = false
    categoriesApi
      .list(undefined, 0, 7)
      .then((page) => {
        if (!cancelled) setCategories(page.content.map((c) => c.name))
      })
      .catch(() => {
        if (!cancelled) setCategories([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  const visibleFollowed = (followed ?? []).slice(0, 5)
  const visibleCategories = (categories ?? CATEGORY_ICONS.map((c) => c.name)).slice(0, 7)

  function iconFor(name: string) {
    return CATEGORY_ICONS.find((c) => c.name.toLowerCase() === name.toLowerCase())?.Icon ?? ChatCircleDots
  }

  return (
    <aside
      aria-label="Navigation latérale FLICK"
      className={cn(
        'sticky top-16 hidden h-[calc(100vh-4rem)] shrink-0 flex-col border-r border-border-default bg-bg-secondary transition-[width] duration-200 md:flex',
        collapsed ? 'w-[72px]' : 'w-60',
      )}
    >
      <div className="flex justify-end px-2 pt-2">
        <button
          type="button"
          onClick={() => setCollapsed((v) => !v)}
          aria-label={collapsed ? 'Déplier la sidebar' : 'Replier la sidebar'}
          className="flex h-7 w-7 items-center justify-center rounded-md text-text-muted hover:bg-bg-hover hover:text-text-primary"
        >
          {collapsed ? <CaretRight size={16} /> : <CaretLeft size={16} />}
        </button>
      </div>

      <nav className="flex-1 space-y-5 overflow-y-auto px-2.5 py-2" aria-label="Chaînes et catégories">
        {/* Chaînes suivies */}
        <section aria-label="Chaînes suivies">
          {!collapsed && (
            <p className="px-2 pb-2 text-[11px] font-bold tracking-widest text-text-muted uppercase">
              Chaînes suivies
            </p>
          )}
          {visibleFollowed.length === 0 ? (
            !collapsed ? (
              <p className="px-2 text-xs text-text-muted">
                Aucune chaîne suivie pour le moment.
              </p>
            ) : null
          ) : (
          <ul className="space-y-0.5">
            {visibleFollowed.map((channel) => (
              <li key={channel.id}>
                <Link
                  to={`/channel/${channel.slug}`}
                  title={`${channel.name} — ${channel.category}`}
                  className={cn(
                    'group flex items-center gap-2.5 rounded-lg px-2 py-1.5 transition-colors hover:bg-bg-surface',
                    collapsed && 'justify-center px-0',
                  )}
                >
                  <span className="relative shrink-0">
                    <FlickAvatar name={channel.name} hue={Math.abs(channel.id.length * 37) % 360} size="sm" />
                    {channel.isLive && (
                      <span className="absolute -right-0.5 -bottom-0.5 h-2.5 w-2.5 rounded-full border-2 border-bg-secondary bg-live" />
                    )}
                  </span>
                  {!collapsed && (
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-text-primary group-hover:text-primary-400">
                        {channel.name}
                      </span>
                      <span className="block truncate text-xs text-text-muted">
                        {channel.category}
                        {channel.isLive && (
                          <> · <span className="font-semibold text-live">● {channel.viewers}</span></>
                        )}
                      </span>
                    </span>
                  )}
                </Link>
              </li>
            ))}
          </ul>
          )}
          {!collapsed && (
            <Link to="/following" className="mt-1 block rounded-lg px-2 py-1.5 text-xs font-medium text-text-muted hover:text-primary-500">
              Afficher plus →
            </Link>
          )}
        </section>

        {/* Catégories */}
        <section aria-label="Catégories">
          {!collapsed && (
            <p className="px-2 pb-2 text-[11px] font-bold tracking-widest text-text-muted uppercase">
              Catégories
            </p>
          )}
          <ul className="space-y-0.5">
            {visibleCategories.map((name) => {
              const Icon = iconFor(name)
              return (
              <li key={name}>
                <Link
                  to={`/categories?search=${encodeURIComponent(name)}`}
                  title={name}
                  className={cn(
                    'flex items-center gap-3 rounded-lg px-2.5 py-2 text-sm transition-colors hover:bg-bg-surface',
                    collapsed && 'justify-center px-0',
                  )}
                >
                  <Icon size={19} className="shrink-0 text-text-secondary" />
                  {!collapsed && (
                    <span className="min-w-0 flex-1 truncate text-text-secondary">{name}</span>
                  )}
                </Link>
              </li>
              )
            })}
          </ul>
        </section>

        {/* Widget créateur */}
        {!collapsed && (
          <div className="rounded-xl border border-border-default bg-gradient-to-b from-bg-surface to-bg-secondary p-3.5">
            <div className="flex items-center gap-2">
              <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary-500 to-[#2563EB] text-sm font-black text-black">
                F
              </span>
              <p className="text-sm font-bold text-text-primary">Deviens créateur sur FLICK</p>
            </div>
            <p className="mt-2 text-xs leading-relaxed text-text-secondary">
              Partage ta passion avec des millions de fans en Afrique.
            </p>
            <Link
              to="/creator/stream"
              className="mt-3 flex h-9 items-center justify-center rounded-lg bg-primary-500 text-sm font-bold text-black transition-all hover:bg-primary-300 hover:shadow-glow-primary"
            >
              Lancer mon live
            </Link>
          </div>
        )}
      </nav>

      {/* Indicateur live global */}
      <div className="border-t border-border-default p-2.5">
        {!collapsed ? <FlickLiveBadge className="w-full justify-center" /> : null}
      </div>
    </aside>
  )
}
