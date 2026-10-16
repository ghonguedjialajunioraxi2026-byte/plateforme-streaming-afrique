import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { ReportCard } from '../../components/moderation/ReportCard'
import { formatCount } from '../../utils/format'
import { moderationApi, type ModerationDashboard as Dashboard } from '../../services/api/moderation'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Centre de modération (étape 17) : /moderation.
/// File OPEN récente + compteurs. Mêmes données et workflow que
/// /admin/reports (périmètre modérateur).
export default function ModerationDashboard() {
  usePageMeta('Modération', 'Centre de modération de la plateforme')
  const toast = useToast()
  const [data, setData] = useState<Dashboard | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const result = await moderationApi.dashboard()
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Centre de modération inaccessible', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Modération</h1>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      )}

      {status === 'error' && (
        <Card><p className="text-sm text-danger" role="alert">{error ?? 'Erreur inconnue.'}</p></Card>
      )}

      {status === 'ready' && data !== null && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Signalements ouverts" value={formatCount(data.openReports)} hint="à traiter" />
            <StatCard label="Sanctions actives" value={formatCount(data.activeSanctions)} hint="en cours" />
          </div>

          <div className="flex flex-wrap gap-2 text-sm">
            <Link to="/moderation/reports" className="text-primary-400 hover:underline">File des signalements →</Link>
            <Link to="/moderation/chat" className="text-primary-400 hover:underline">Modération du chat →</Link>
            <Link to="/moderation/users" className="text-primary-400 hover:underline">Comptes →</Link>
          </div>

          <section aria-label="Signalements récents">
            <h2 className="mb-2 font-display text-lg font-bold text-text-primary">À traiter en priorité</h2>
            {data.recentOpen.content.length === 0 ? (
              <Card><p className="text-sm text-text-secondary">File vide — rien à modérer.</p></Card>
            ) : (
              <div className="space-y-2">
                {data.recentOpen.content.map((report) => (
                  <ReportCard key={report.id} report={report} />
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}
