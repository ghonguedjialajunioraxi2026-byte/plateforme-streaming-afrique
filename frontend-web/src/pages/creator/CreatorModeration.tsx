import { useEffect, useState } from 'react'
import { Badge, Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { formatDate } from '../../utils/format'
import { creatorDashboardApi, type CreatorModeration } from '../../services/api/dashboard'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Modération de son espace (étape 16) : /creator/moderation.
/// Signalements ouverts visant directement la chaîne (périmètre du
/// créateur uniquement — pas la file globale des modérateurs). Les actions
/// (ban, timeout…) restent effectuées via les outils existants selon les rôles.
export default function CreatorModeration() {
  usePageMeta('Modération', 'Signalements visant votre chaîne')
  const toast = useToast()
  const [data, setData] = useState<CreatorModeration | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const result = await creatorDashboardApi.moderation()
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Modération inaccessible', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Modération</h1>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement de la modération">
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
          <section aria-label="Compteur">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard
                label="Signalements ouverts"
                value={String(data.openReports)}
                hint={data.openReports === 0 ? 'votre espace est sain' : 'visant votre chaîne'}
              />
            </div>
          </section>

          <section aria-label="Signalements ouverts">
            <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Signalements ouverts</h2>
            {data.recentOpenReports.length === 0 ? (
              <Card>
                <p className="text-sm text-text-secondary">
                  Aucun signalement ouvert — votre espace est sain. Les signalements
                  traités par la modération plateforme n'apparaissent pas ici.
                </p>
              </Card>
            ) : (
              <div className="space-y-2">
                {data.recentOpenReports.map((report) => (
                  <Card key={report.id}>
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
                          Signalement #{report.id}
                          <Badge variant="warning">{report.reason ?? 'Autre'}</Badge>
                        </p>
                        {report.description !== null && report.description.length > 0 && (
                          <p className="mt-1 line-clamp-2 text-xs text-text-secondary">{report.description}</p>
                        )}
                        <p className="mt-1 text-xs text-text-muted">{formatDate(report.createdAt)}</p>
                      </div>
                      <Badge variant="neutral">{report.status ?? 'OPEN'}</Badge>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
