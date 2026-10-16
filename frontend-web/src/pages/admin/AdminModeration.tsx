import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Loader, Tabs, useToast } from '../../components/ui'
import { Pagination } from '../../components/admin/Pagination'
import { formatCount, formatDate } from '../../utils/format'
import { moderationApi, type Sanction, type SanctionState } from '../../services/api/moderation'
import { usePageMeta } from '../../hooks/usePageMeta'

const PAGE_SIZE = 20
const TABS = [
  { key: '', label: 'Toutes' },
  { key: 'ACTIVE', label: 'Actives' },
  { key: 'LIFTED', label: 'Levées' },
  { key: 'EXPIRED', label: 'Expirées' },
]

function stateBadge(state: SanctionState) {
  if (state === 'ACTIVE') return <Badge variant="danger">Active</Badge>
  if (state === 'LIFTED') return <Badge variant="accent">Levée</Badge>
  return <Badge variant="neutral">Expirée</Badge>
}

/// Historique des sanctions (étape 17) : /admin/moderation.
/// Levée motivée implicitement (acte audité + réactivation auto si dernière
/// mesure). Le prononcé des sanctions vit sur /admin/reports.
export default function AdminModeration() {
  usePageMeta('Modération', 'Historique des sanctions de la plateforme')
  const toast = useToast()
  const [filter, setFilter] = useState<SanctionState | ''>('')
  const [actions, setActions] = useState<Sanction[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async (nextFilter: SanctionState | '', nextPage: number) => {
    const result = await moderationApi.actions(
      nextFilter === '' ? undefined : nextFilter, nextPage, PAGE_SIZE)
    setActions(result.content)
    setPage(result.page)
    setTotalPages(result.totalPages)
    setTotalElements(result.totalElements)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload(filter, page)
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
  }, [reload, filter, page])

  async function handleLift(sanction: Sanction) {
    if (!window.confirm(`Lever la sanction #${sanction.id} (${sanction.action}) ?`)) return
    try {
      const updated = await moderationApi.lift(sanction.id)
      setActions((current) => current.map((a) => (a.id === updated.id ? updated : a)))
      toast.show('Sanction levée — acte audité', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action impossible', 'error')
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Modération</h1>

      <Tabs
        items={TABS}
        activeKey={filter}
        onChange={(key) => { setFilter(key as SanctionState | ''); setPage(0) }}
      />

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : actions.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucune sanction dans cet état.</p></Card>
      ) : (
        <div className="space-y-2">
          {actions.map((sanction) => (
            <Card key={sanction.id}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
                    Sanction #{sanction.id}
                    {stateBadge(sanction.state)}
                    <Badge variant="neutral">{sanction.action}</Badge>
                    <Badge variant="neutral">{sanction.scope}</Badge>
                  </p>
                  <p className="mt-1 text-xs text-text-secondary">
                    {sanction.reportId !== null && `Signalement #${sanction.reportId} · `}
                    modérateur #{sanction.moderatorId}
                    {sanction.targetUserId !== null && ` · compte #${sanction.targetUserId}`}
                    {' · '}{formatDate(sanction.createdAt)}
                    {sanction.endsAt !== null && ` · jusqu’au ${formatDate(sanction.endsAt)}`}
                  </p>
                  <p className="mt-1 line-clamp-2 text-xs text-text-secondary">{sanction.reason}</p>
                </div>
                {sanction.state === 'ACTIVE' && (
                  <Button variant="ghost" size="sm" onClick={() => handleLift(sanction)}>
                    Lever
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      <p className="text-xs text-text-muted">{formatCount(totalElements)} sanction(s).</p>
    </div>
  )
}
