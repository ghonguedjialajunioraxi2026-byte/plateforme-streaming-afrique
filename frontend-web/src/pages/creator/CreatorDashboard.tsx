import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Avatar, Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate } from '../../utils/format'
import { creatorDashboardApi, type CreatorDashboard } from '../../services/api/dashboard'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Page principale du Creator Studio (étape 16) : /creator.
/// Centre de contrôle : état de la chaîne, live en cours (viewers Redis
/// temps réel), cartes (followers, abonnés, VOD, clips, cadeaux, coins),
/// activité récente et accès rapides. Données 100 % backend.
export default function CreatorDashboard() {
  usePageMeta('Creator Studio', 'Centre de contrôle de votre chaîne')
  const toast = useToast()
  const [dashboard, setDashboard] = useState<CreatorDashboard | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'no-channel' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await creatorDashboardApi.overview()
        if (!cancelled) {
          setDashboard(data)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        const message = loadError instanceof Error ? loadError.message : ''
        if (message.includes('Aucune chaîne')) {
          setStatus('no-channel')
        } else {
          setError(message.length > 0 ? message : 'Chargement impossible')
          setStatus('error')
          toast.show('Tableau de bord inaccessible', 'error')
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  if (status === 'loading') {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="Chargement du tableau de bord">
        <Loader size="lg" />
      </div>
    )
  }

  if (status === 'no-channel') {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Creator Studio</h1>
        <Card>
          <p className="font-semibold text-text-primary">Aucune chaîne pour ce compte</p>
          <p className="mt-1 text-sm text-text-secondary">
            Ouvrez votre chaîne pour débloquer le studio : directs, VOD, clips, revenus et communauté.
          </p>
          <div className="mt-3">
            <Link to="/creator/settings">
              <Button variant="primary" size="sm">Ouvrir ma chaîne</Button>
            </Link>
          </div>
        </Card>
      </div>
    )
  }

  if (status === 'error' || dashboard === null) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Creator Studio</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      </div>
    )
  }

  const { channel, live, wallet } = dashboard
  const avatar = mediaUrl(channel.avatarUrl) ?? undefined

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex flex-wrap items-center gap-4">
        <Avatar src={avatar} name={channel.displayName} size="lg" />
        <div className="min-w-0 flex-1">
          <p className="text-xs text-text-muted">Bienvenue,</p>
          <h1 className="truncate font-display text-2xl font-bold text-text-primary">
            {channel.displayName}
          </h1>
          <p className="mt-0.5 flex flex-wrap items-center gap-2 text-sm text-text-secondary">
            <span className="font-mono text-xs">@{channel.slug}</span>
            {live !== null ? (
              <Badge variant="live" dot>EN DIRECT</Badge>
            ) : (
              <Badge variant="warning">HORS LIGNE</Badge>
            )}
          </p>
        </div>
        <Link to="/creator/stream">
          <Button variant="primary" size="sm">Ouvrir le studio</Button>
        </Link>
      </header>

      {live !== null && (
        <Card className="border-danger/40">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="min-w-0">
              <p className="flex items-center gap-2 font-semibold text-text-primary">
                <Badge variant="live" dot>LIVE</Badge>
                <span className="truncate">{live.title}</span>
              </p>
              <p className="mt-1 text-xs text-text-muted">
                {formatCount(live.viewers)} spectateurs · pic {formatCount(live.peakViewers)}
                {' · '}depuis {formatDate(live.startedAt)}
              </p>
            </div>
            <Link to="/creator/stream">
              <Button variant="secondary" size="sm">Contrôler le live</Button>
            </Link>
          </div>
        </Card>
      )}

      <section aria-label="Statistiques principales">
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <StatCard label="Spectateurs" value={formatCount(live?.viewers ?? 0)} hint={live !== null ? 'en ce moment' : 'hors ligne'} />
          <StatCard label="Followers" value={formatCount(dashboard.followers)} hint="suivi gratuit" />
          <StatCard label="Abonnés" value={formatCount(dashboard.subscribers)} hint="abonnements payants" />
          <StatCard label="Notifications" value={formatCount(dashboard.unreadNotifications)} hint="non lues" />
          <StatCard label="VOD publiées" value={formatCount(dashboard.videosTotal)} hint={`${formatCount(dashboard.videoViews)} vues`} />
          <StatCard label="Clips" value={formatCount(dashboard.clipsTotal)} hint={`${formatCount(dashboard.clipViews)} vues`} />
          <StatCard label="Cadeaux reçus" value={formatCount(dashboard.giftsReceived)} hint={`${formatCount(dashboard.giftCoinsEarned)} coins`} />
          <StatCard
            label="Coins disponibles"
            value={wallet !== null ? formatCount(wallet.availableCoins) : '—'}
            hint={wallet !== null ? `réservés : ${formatCount(wallet.reservedCoins)}` : 'aucun portefeuille'}
          />
        </div>
      </section>

      <div className="grid gap-4 lg:grid-cols-2">
        <section aria-label="Activité récente">
          <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Activité récente</h2>
          {dashboard.recentActivity.length === 0 ? (
            <Card>
              <p className="text-sm text-text-secondary">Aucune activité récente.</p>
            </Card>
          ) : (
            <div className="space-y-2">
              {dashboard.recentActivity.map((item) => (
                <Card key={item.id}>
                  <p className="text-sm font-semibold text-text-primary">{item.title}</p>
                  <p className="mt-0.5 line-clamp-2 text-xs text-text-secondary">{item.message}</p>
                  <p className="mt-1 text-xs text-text-muted">{formatDate(item.createdAt)}</p>
                </Card>
              ))}
            </div>
          )}
        </section>

        <section aria-label="Accès rapides">
          <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Accès rapides</h2>
          <div className="grid grid-cols-2 gap-2">
            {[
              { to: '/creator/stream', label: 'Studio live', desc: 'Démarrer, arrêter' },
              { to: '/creator/videos', label: 'Mes VOD', desc: `${formatCount(dashboard.videosTotal)} vidéos` },
              { to: '/creator/clips', label: 'Mes clips', desc: `${formatCount(dashboard.clipsTotal)} clips` },
              { to: '/creator/analytics', label: 'Analytique', desc: 'Performance' },
              { to: '/creator/revenue', label: 'Revenus', desc: 'Coins et journal' },
              { to: '/creator/community', label: 'Communauté', desc: `${formatCount(dashboard.followers)} followers` },
              { to: '/creator/moderation', label: 'Modération', desc: 'Votre espace' },
              { to: '/creator/settings', label: 'Paramètres', desc: 'Chaîne' },
            ].map((link) => (
              <Link key={link.to} to={link.to}>
                <Card className="transition-colors hover:border-primary-400/50">
                  <p className="text-sm font-semibold text-text-primary">{link.label}</p>
                  <p className="mt-0.5 text-xs text-text-muted">{link.desc}</p>
                </Card>
              </Link>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
