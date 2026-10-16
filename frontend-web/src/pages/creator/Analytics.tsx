import { useEffect, useState } from 'react'
import { Badge, Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { VideoCard, ClipCard } from '../../components/video'
import { formatCount } from '../../utils/format'
import { creatorDashboardApi, type CreatorAnalytics } from '../../services/api/dashboard'
import { usePageMeta } from '../../hooks/usePageMeta'

const PERIODS = [
  { days: 1, label: "Aujourd'hui" },
  { days: 7, label: '7 jours' },
  { days: 30, label: '30 jours' },
  { days: 90, label: '90 jours' },
] as const

/// Analytique de la chaîne (étape 16) : /creator/analytics.
/// Totaux backend + courbes des snapshots (état vide honnête si non
/// consolidés) + top VOD/clips. Barres CSS — aucune lib de graphiques.
export default function Analytics() {
  usePageMeta('Analytique', 'Performance de votre chaîne')
  const toast = useToast()
  const [days, setDays] = useState<number>(30)
  const [data, setData] = useState<CreatorAnalytics | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStatus('loading')
      try {
        const result = await creatorDashboardApi.analytics(days)
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Analytique inaccessible', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [days, toast])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-bold text-text-primary">Analytique</h1>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Période">
          {PERIODS.map((period) => (
            <button
              key={period.days}
              type="button"
              onClick={() => setDays(period.days)}
              aria-pressed={days === period.days}
              className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                days === period.days
                  ? 'bg-primary-500 text-text-inverse'
                  : 'bg-bg-elevated text-text-secondary hover:text-text-primary'
              }`}
            >
              {period.label}
            </button>
          ))}
        </div>
      </div>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement de l'analytique">
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
          <section aria-label="Totaux de la période">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="Vues VOD" value={formatCount(data.videoViews)} />
              <StatCard label="Vues clips" value={formatCount(data.clipViews)} />
              <StatCard label="Followers" value={formatCount(data.followers)} />
              <StatCard label="Abonnés" value={formatCount(data.subscribers)} />
              <StatCard label="Lives" value={formatCount(data.lives)} hint="directs réalisés" />
              <StatCard label="Cadeaux" value={formatCount(data.giftsReceived)} hint={`${formatCount(data.giftCoinsEarned)} coins`} />
            </div>
          </section>

          <section aria-label="Courbes">
            <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Évolution</h2>
            {data.series.length === 0 ? (
              <Card>
                <p className="text-sm text-text-secondary">
                  Pas encore de données consolidées sur cette période — les courbes apparaîtront
                  dès que la consolidation écrira des snapshots (les totaux ci-dessus sont exacts).
                </p>
              </Card>
            ) : (
              <SeriesChart series={data.series} />
            )}
          </section>

          <div className="grid gap-4 lg:grid-cols-2">
            <section aria-label="Top VOD">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Top VOD</h2>
              {data.topVideos.length === 0 ? (
                <Card><p className="text-sm text-text-secondary">Aucune VOD pour le moment.</p></Card>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {data.topVideos.map((video) => (
                    <VideoCard key={video.id} video={video} />
                  ))}
                </div>
              )}
            </section>
            <section aria-label="Top clips">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Top clips</h2>
              {data.topClips.length === 0 ? (
                <Card><p className="text-sm text-text-secondary">Aucun clip pour le moment.</p></Card>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {data.topClips.map((clip) => (
                    <ClipCard key={clip.id} clip={clip} />
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

/// Barres CSS groupées par métrique (aucune dépendance graphique).
function SeriesChart({ series }: { series: CreatorAnalytics['series'] }) {
  const byMetric = new Map<string, typeof series>()
  for (const point of series) {
    const group = byMetric.get(point.metric) ?? []
    group.push(point)
    byMetric.set(point.metric, group)
  }
  return (
    <div className="space-y-4">
      {[...byMetric.entries()].map(([metric, points]) => {
        const max = Math.max(1, ...points.map((p) => p.value))
        return (
          <Card key={metric}>
            <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-text-primary">
              {metric}
              <Badge variant="accent">{formatCount(points.reduce((sum, p) => sum + p.value, 0))}</Badge>
            </p>
            <div className="flex h-24 items-end gap-1" role="img" aria-label={`Évolution ${metric}`}>
              {points.map((point) => (
                <div
                  key={`${point.periodStart}`}
                  className="min-w-0 flex-1 rounded-t bg-primary-500/70"
                  style={{ height: `${Math.max(4, Math.round((point.value / max) * 100))}%` }}
                  title={`${point.periodStart} : ${point.value}`}
                />
              ))}
            </div>
          </Card>
        )
      })}
    </div>
  )
}
