import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { CreatorCard } from '../../components/streaming'
import { MobileChip, MobileChipRail } from '../../components/mobile'
import { Avatar, EmptyState, Skeleton } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { usePageMeta } from '../../hooks/usePageMeta'
import { useAuth } from '../../features/auth/useAuth'
import { followsApi } from '../../services/api/follows'
import { walletApi, type WalletMovement } from '../../services/api/wallet'
import { formatCount } from '../../utils/format'
import { formatDate } from '../../utils/format'
import type { ChannelDetail } from '../../types/domain'

type Tab = 'active' | 'history'

/** Historique = mouvements liés aux abonnements payants (journal réel). */
function isSubscriptionMovement(kind: string): boolean {
  const key = kind.toLowerCase()
  return key.includes('subscription') || key.includes('abonn')
}

/**
 * Mes abonnements (maquette écran 17) :
 * - Actifs : chaînes suivies (follows serveur) en lignes mobiles ;
 * - Historique : abonnements payés issus du journal wallet (réel).
 * Le backend n'expose pas encore d'endpoint « abonnements payants » :
 * aucun abonnement fictif affiché.
 */
export default function Subscriptions() {
  usePageMeta('Mes abonnements', 'Tes abonnements aux chaînes de créateurs sur FLICK.')
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('active')
  const [channels, setChannels] = useState<ChannelDetail[]>([])
  const [history, setHistory] = useState<WalletMovement[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (user === null) return
    setLoading(true)
    try {
      const [followed, journal] = await Promise.all([
        followsApi.followedChannels(user.id, 0, 30),
        walletApi.transactions(0, 30).catch(() => null),
      ])
      setChannels(followed.content)
      setHistory((journal?.content ?? []).filter((movement) => isSubscriptionMovement(movement.kind)))
    } catch {
      setChannels([])
      setHistory([])
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="mx-auto w-full max-w-3xl space-y-4">
      <h1 className="font-display text-[22px] font-bold tracking-tight text-text-primary md:text-2xl">
        Mes abonnements
      </h1>

      <MobileChipRail label="Filtrer les abonnements">
        <MobileChip label="Actifs" active={tab === 'active'} onClick={() => setTab('active')} count={channels.length} />
        <MobileChip label="Historique" active={tab === 'history'} onClick={() => setTab('history')} />
      </MobileChipRail>

      {loading && (
        <div className="space-y-2" aria-busy="true" aria-label="Chargement des abonnements">
          {Array.from({ length: 4 }).map((_, index) => (
            <div key={index} className="flex items-center gap-3 rounded-2xl border border-border-default bg-bg-surface p-4">
              <Skeleton className="h-11 w-11 shrink-0 rounded-full" />
              <div className="flex-1 space-y-2">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-3 w-1/3" />
              </div>
            </div>
          ))}
        </div>
      )}

      {!loading && tab === 'active' && (
        channels.length === 0 ? (
          <EmptyState
            icon="star"
            title="Aucun abonnement actif"
            description="Suis des créateurs pour les retrouver ici et être alerté de leurs lives."
            action={
              <Link
                to="/explorer"
                className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
              >
                Découvrir des créateurs
              </Link>
            }
          />
        ) : (
          <>
            {/* Mobile : lignes maquette */}
            <ul className="space-y-2 md:hidden">
              {channels.map((channel) => (
                <li key={channel.id}>
                  <Link
                    to={`/channel/${channel.slug}`}
                    className="flex min-h-11 items-center gap-3 rounded-2xl border border-border-default bg-bg-surface p-3 transition-colors active:bg-bg-elevated"
                  >
                    <Avatar name={channel.displayName} size="md" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-[15px] font-semibold text-text-primary">
                        {channel.displayName}
                      </span>
                      <span className="block truncate text-[13px] text-text-muted">
                        {formatCount(channel.followerCount)} abonnés
                      </span>
                    </span>
                    <span className="flex shrink-0 items-center gap-1 rounded-full bg-primary-500/15 px-2.5 py-1 text-[11px] font-bold text-primary-300">
                      <span aria-hidden="true" className="h-1.5 w-1.5 rounded-full bg-primary-500" />
                      Actif
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {/* Desktop : cartes */}
            <div className="hidden gap-3 sm:grid-cols-2 md:grid">
              {channels.map((channel) => (
                <CreatorCard
                  key={channel.id}
                  channel={{
                    id: String(channel.id),
                    userId: String(channel.userId),
                    name: channel.displayName,
                    slug: channel.slug,
                    description: channel.bio ?? undefined,
                    followersCount: channel.followerCount,
                    isLive: false,
                  }}
                />
              ))}
            </div>
            <Link
              to="/following"
              className="flex min-h-11 items-center justify-center gap-1.5 rounded-xl border border-border-default text-sm font-semibold text-primary-400 transition-colors active:bg-bg-elevated"
            >
              <Icon name="settings" className="h-4 w-4" />
              Gérer mes abonnements
            </Link>
          </>
        )
      )}

      {!loading && tab === 'history' && (
        history.length === 0 ? (
          <EmptyState
            icon="clock"
            title="Aucun abonnement payé"
            description="Tes abonnements payants (coins) apparaîtront ici dès ton premier soutien à une chaîne."
            action={
              <Link
                to="/explorer"
                className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
              >
                Explorer les lives
              </Link>
            }
          />
        ) : (
          <ul className="divide-y divide-border-default rounded-2xl border border-border-default bg-bg-surface">
            {history.map((movement) => (
              <li key={movement.id} className="flex items-center gap-3 px-4 py-3">
                <span
                  aria-hidden="true"
                  className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-warning/15 text-warning"
                >
                  <Icon name="star" className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-text-primary">
                    Abonnement
                  </span>
                  <span className="block text-xs text-text-muted">
                    {formatDate(movement.createdAt)}
                  </span>
                </span>
                <span className="shrink-0 text-sm font-bold text-text-secondary">
                  −{movement.amountCoins.toLocaleString('fr-FR')}
                </span>
              </li>
            ))}
          </ul>
        )
      )}
    </div>
  )
}
