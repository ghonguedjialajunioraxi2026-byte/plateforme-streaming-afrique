import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { EmptyState, Skeleton, useToast } from '../../components/ui'
import { MobileChip, MobileChipRail } from '../../components/mobile'
import { SectionHeader } from '../../components/common/SectionHeader'
import { Icon } from '../../components/ui/icons'
import { usePageMeta } from '../../hooks/usePageMeta'
import { useAuth } from '../../features/auth/useAuth'
import { notificationsApi, type NotificationItem } from '../../services/api/notifications'
import { cn } from '../../utils/cn'

/** Temps relatif court (« il y a 5 min »). */
function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  if (Number.isNaN(diff) || diff < 0) return ''
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return 'À l\'instant'
  if (minutes < 60) return `Il y a ${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `Il y a ${hours} h`
  const days = Math.floor(hours / 24)
  if (days < 7) return `Il y a ${days} j`
  return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' })
}

/**
 * Notifications façon Twitch : non-lues en avant (pastille verte),
 * « Tout marquer comme lu », lien vers le contenu concerné,
 * temps relatif. Skeletons + état vide travaillé.
 */
export default function Notifications() {
  usePageMeta('Notifications', 'Tes alertes : lives de tes créateurs, réponses, annonces Flick.')
  const { user } = useAuth()
  const toast = useToast()
  const [items, setItems] = useState<NotificationItem[]>([])
  const [loading, setLoading] = useState(true)
  const [markingAll, setMarkingAll] = useState(false)
  const [filter, setFilter] = useState<'all' | 'follow' | 'live' | 'gift'>('all')

  const load = useCallback(async () => {
    if (user === null) return
    setLoading(true)
    try {
      const page = await notificationsApi.list(user.id, 0, 30)
      setItems(page.content)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    void load()
  }, [load])

  async function markAll() {
    if (user === null || markingAll) return
    setMarkingAll(true)
    try {
      await notificationsApi.markAllAsRead(user.id)
      setItems((current) => current.map((item) => ({ ...item, isRead: true })))
      toast.show('Toutes les notifications sont lues.', 'success')
    } catch {
      toast.show('Action impossible pour le moment.', 'error')
    } finally {
      setMarkingAll(false)
    }
  }

  async function markOne(item: NotificationItem) {
    if (item.isRead) return
    try {
      await notificationsApi.markAsRead(item.id)
      setItems((current) =>
        current.map((candidate) =>
          candidate.id === item.id ? { ...candidate, isRead: true } : candidate,
        ),
      )
    } catch {
      // Lecture silencieusement ignorée : la liste reste correcte.
    }
  }

  const unread = items.filter((item) => !item.isRead).length

  /** Filtre maquette (mots-clés FR/EN sur type + titre + message). */
  const visible = items.filter((item) => {
    if (filter === 'all') return true
    const haystack = `${item.type} ${item.title} ${item.message}`.toLowerCase()
    if (filter === 'follow') return /follow|suivi|abonn|subscriber/.test(haystack)
    if (filter === 'live') return /live|direct|stream/.test(haystack)
    return /gift|cadeau|coin|donat/.test(haystack)
  })

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <SectionHeader
        title="Notifications"
        subtitle={unread > 0 ? `${unread} non lue${unread > 1 ? 's' : ''}` : 'Tu es à jour'}
        action={
          unread > 0 ? (
            <button
              type="button"
              onClick={() => void markAll()}
              disabled={markingAll}
              className="rounded-lg px-3 py-1.5 text-sm font-semibold text-primary-400 transition-colors duration-200 hover:bg-primary-500/10 hover:text-primary-300 disabled:opacity-50"
            >
              {markingAll ? '…' : 'Tout marquer comme lu'}
            </button>
          ) : undefined
        }
      />

      {loading && (
        <div className="space-y-2" aria-busy="true" aria-label="Chargement des notifications">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="flex items-center gap-3 rounded-xl border border-border-default bg-bg-surface p-4">
              <Skeleton className="h-10 w-10 shrink-0 rounded-full" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-2/3" />
                <Skeleton className="h-3 w-1/3" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && items.length === 0 && (
        <EmptyState
          icon="bell"
          title="Aucune notification"
          description="Suis des créateurs pour être alerté de leurs lives, et reviens ici pour ne rien manquer."
          action={
            <Link
              to="/explorer"
              className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
            >
              Découvrir des créateurs
            </Link>
          }
        />
      )}

      {!loading && items.length > 0 && (
        <>
          {/* Mobile : chips maquette */}
          <MobileChipRail label="Filtrer les notifications">
            <MobileChip label="Tout" active={filter === 'all'} onClick={() => setFilter('all')} />
            <MobileChip label="Suivis" active={filter === 'follow'} onClick={() => setFilter('follow')} />
            <MobileChip label="En direct" active={filter === 'live'} onClick={() => setFilter('live')} />
            <MobileChip label="Cadeaux" active={filter === 'gift'} onClick={() => setFilter('gift')} />
          </MobileChipRail>
          {visible.length === 0 ? (
            <EmptyState
              icon="bell"
              title="Rien dans cette catégorie"
              description="Aucune notification de ce type pour le moment."
            />
          ) : (
          <ul className="space-y-2">
          {visible.map((item) => {
            const body = (
              <>
                <span
                  className={cn(
                    'flex h-10 w-10 shrink-0 items-center justify-center rounded-full transition-colors duration-200',
                    item.isRead ? 'bg-bg-elevated text-text-muted' : 'bg-primary-500/15 text-primary-300',
                  )}
                >
                  <Icon name="bell" className="h-5 w-5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex items-center gap-2">
                    <span className="truncate text-sm font-semibold text-text-primary">
                      {item.title}
                    </span>
                    {!item.isRead && (
                      <span aria-label="Non lue" className="h-2 w-2 shrink-0 rounded-full bg-primary-500" />
                    )}
                  </span>
                  <span className="mt-0.5 line-clamp-2 block text-sm text-text-secondary">
                    {item.message}
                  </span>
                  <span className="mt-1 block text-xs text-text-muted">
                    {relativeTime(item.createdAt)}
                  </span>
                </span>
              </>
            )
            const rowClass = cn(
              'animate-fade-in flex items-center gap-3 rounded-xl border p-4 text-left transition-colors duration-200',
              item.isRead
                ? 'border-border-default bg-bg-surface'
                : 'border-primary-500/30 bg-primary-500/5 hover:bg-primary-500/10',
            )
            return (
              <li key={item.id}>
                {item.linkUrl ? (
                  <Link to={item.linkUrl} onClick={() => void markOne(item)} className={rowClass}>
                    {body}
                  </Link>
                ) : (
                  <button type="button" onClick={() => void markOne(item)} className={cn(rowClass, 'w-full')}>
                    {body}
                  </button>
                )}
              </li>
            )
          })}
        </ul>
          )}
        </>
      )}
    </div>
  )
}
