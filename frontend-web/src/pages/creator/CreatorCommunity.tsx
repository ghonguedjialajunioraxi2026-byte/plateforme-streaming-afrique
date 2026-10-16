import { useEffect, useState } from 'react'
import { Avatar, Badge, Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate } from '../../utils/format'
import { creatorDashboardApi, type CreatorCommunity } from '../../services/api/dashboard'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Communauté de la chaîne (étape 16) : /creator/community.
/// Followers (gratuit) et abonnés (payants) séparés — jamais fusionnés.
/// Profils publics uniquement (jamais d'email).
export default function CreatorCommunity() {
  usePageMeta('Communauté', 'Followers et abonnés de votre chaîne')
  const toast = useToast()
  const [data, setData] = useState<CreatorCommunity | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const result = await creatorDashboardApi.community()
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Communauté inaccessible', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Communauté</h1>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement de la communauté">
          <Loader size="lg" />
        </div>
      )}

      {status === 'error' && (
        <Card>
          <p className="text-sm text-danger" role="alert">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      )}

      {status === 'ready' && data !== null && (
        <>
          <section aria-label="Compteurs">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="Followers" value={formatCount(data.followersCount)} hint="suivi gratuit" />
              <StatCard label="Abonnés" value={formatCount(data.subscribersCount)} hint="abonnements payants" />
            </div>
          </section>

          <div className="grid gap-4 lg:grid-cols-2">
            <section aria-label="Nouveaux followers">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Nouveaux followers</h2>
              {data.recentFollowers.length === 0 ? (
                <Card>
                  <p className="text-sm text-text-secondary">Aucun follower récent.</p>
                </Card>
              ) : (
                <div className="space-y-2">
                  {data.recentFollowers.map((member) => (
                    <Card key={member.userId}>
                      <div className="flex items-center gap-3">
                        <Avatar src={mediaUrl(member.avatarUrl) ?? undefined} name={member.displayName ?? member.username} size="sm" />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold text-text-primary">
                            {member.displayName ?? member.username}
                          </p>
                          <p className="text-xs text-text-muted">@{member.username} · {formatDate(member.followedAt)}</p>
                        </div>
                        <Badge variant="accent">Follower</Badge>
                      </div>
                    </Card>
                  ))}
                </div>
              )}
            </section>

            <section aria-label="Nouveaux abonnés">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Nouveaux abonnés</h2>
              {data.recentSubscribers.length === 0 ? (
                <Card>
                  <p className="text-sm text-text-secondary">Aucun abonné récent.</p>
                </Card>
              ) : (
                <div className="space-y-2">
                  {data.recentSubscribers.map((subscriber) => (
                    <Card key={subscriber.userId}>
                      <div className="flex items-center gap-3">
                        <Avatar name={subscriber.displayName ?? '?'} size="sm" />
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm font-semibold text-text-primary">
                            {subscriber.displayName ?? `Membre ${subscriber.userId}`}
                          </p>
                          <p className="text-xs text-text-muted">
                            {subscriber.planName ?? 'Abonnement'} · depuis {formatDate(subscriber.since)}
                          </p>
                        </div>
                        <Badge variant="primary">Abonné</Badge>
                      </div>
                    </Card>
                  ))}
                </div>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
