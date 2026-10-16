import { useCallback, useEffect, useState } from 'react'
import { Button, Card, Loader, Tabs, useToast } from '../../components/ui'
import { Pagination } from '../../components/admin/Pagination'
import { DecideModal } from '../../components/moderation/DecideModal'
import { ReportCard } from '../../components/moderation/ReportCard'
import { formatCount } from '../../utils/format'
import { moderationApi, type Report, type ReportStatus, type SanctionAction, type SanctionScope } from '../../services/api/moderation'
import { usePageMeta } from '../../hooks/usePageMeta'

const PAGE_SIZE = 20
const TABS = [
  { key: 'OPEN', label: 'Ouverts' },
  { key: 'ACCEPTED', label: 'Retenus' },
  { key: 'REJECTED', label: 'Rejetés' },
]

/// File des signalements côté modérateurs (étape 17) : /moderation/reports.
/// Même workflow que /admin/reports (retenir, rejeter, sanctionner).
export default function ModReports() {
  usePageMeta('Signalements', 'File de modération')
  const toast = useToast()
  const [status, setStatus] = useState<ReportStatus>('OPEN')
  const [reports, setReports] = useState<Report[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deciding, setDeciding] = useState<Report | null>(null)
  const [acting, setActing] = useState(false)

  const reload = useCallback(async (nextStatus: ReportStatus, nextPage: number) => {
    const result = await moderationApi.reports(nextStatus, nextPage, PAGE_SIZE)
    setReports(result.content)
    setPage(result.page)
    setTotalPages(result.totalPages)
    setTotalElements(result.totalElements)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload(status, page)
        if (!cancelled) setError(null)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Chargement impossible')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [reload, status, page])

  function refresh() {
    void reload(status, page).catch((err: unknown) => {
      toast.show(err instanceof Error ? err.message : 'Rechargement impossible', 'error')
    })
  }

  async function handleDecide(input: {
    action: SanctionAction
    scope: SanctionScope
    targetUserId: number | null
    reason: string
    endsAt: string | null
  }) {
    if (deciding === null) return
    setActing(true)
    try {
      await moderationApi.decide(deciding.id, input)
      setDeciding(null)
      toast.show('Sanction appliquée — acte audité', 'success')
      refresh()
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Sanction impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Signalements</h1>

      <Tabs
        items={TABS}
        activeKey={status}
        onChange={(key) => { setStatus(key as ReportStatus); setPage(0) }}
      />

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : reports.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucun signalement dans cet état.</p></Card>
      ) : (
        <div className="space-y-2">
          {reports.map((report) => (
            <ReportCard key={report.id} report={report}>
              {report.status === 'OPEN' && (
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    moderationApi.accept(report.id)
                      .then(() => { toast.show('Signalement retenu', 'success'); refresh() })
                      .catch((err: unknown) => toast.show(err instanceof Error ? err.message : 'Action impossible', 'error'))
                  }}
                >
                  Retenir
                </Button>
              )}
              {report.status !== 'REJECTED' && (
                <Button variant="danger" size="sm" onClick={() => setDeciding(report)}>
                  Sanctionner
                </Button>
              )}
            </ReportCard>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      <p className="text-xs text-text-muted">{formatCount(totalElements)} signalement(s).</p>

      <DecideModal
        open={deciding !== null}
        report={deciding}
        loading={acting}
        onClose={() => setDeciding(null)}
        onConfirm={handleDecide}
      />
    </div>
  )
}
