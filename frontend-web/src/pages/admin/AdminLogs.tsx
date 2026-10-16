import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Button, Card, Input, Loader } from '../../components/ui'
import { Pagination } from '../../components/admin/Pagination'
import { formatCount, formatDate } from '../../utils/format'
import { adminApi, type AuditLog } from '../../services/api/admin'
import { usePageMeta } from '../../hooks/usePageMeta'

const PAGE_SIZE = 20

/// Journal d'audit (étape 17) : /admin/logs.
/// Actes administratifs et de modération (qui, quoi, sur quoi, avant/après,
/// motif). Append-only, jamais de secret. Filtres action/acteur.
export default function AdminLogs() {
  usePageMeta('Logs', "Journal d'audit des actes administratifs")
  const [entries, setEntries] = useState<AuditLog[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [action, setAction] = useState('')
  const [appliedAction, setAppliedAction] = useState('')
  const [actorId, setActorId] = useState('')
  const [appliedActor, setAppliedActor] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async (nextPage: number, nextAction: string, nextActor: string) => {
    const result = await adminApi.audit({
      action: nextAction === '' ? undefined : nextAction,
      actorId: nextActor === '' ? undefined : Number(nextActor),
      page: nextPage,
      size: PAGE_SIZE,
    })
    setEntries(result.content)
    setPage(result.page)
    setTotalPages(result.totalPages)
    setTotalElements(result.totalElements)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload(page, appliedAction, appliedActor)
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
  }, [reload, page, appliedAction, appliedActor])

  function handleFilter(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedAction(action.trim())
    setAppliedActor(actorId.trim())
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Logs d'audit</h1>

      <Card>
        <form onSubmit={handleFilter} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-[180px] flex-1">
            <Input
              label="Action (ex. USER_STATUS_CHANGED)"
              name="audit-action"
              value={action}
              onChange={(e) => setAction(e.target.value)}
              placeholder="MODERATION_DECIDED"
            />
          </div>
          <div className="w-40">
            <Input
              label="Acteur (id)"
              name="audit-actor"
              type="number"
              min={1}
              value={actorId}
              onChange={(e) => setActorId(e.target.value)}
            />
          </div>
          <Button type="submit" variant="secondary" size="sm">Filtrer</Button>
        </form>
      </Card>

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : entries.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucun acte pour ces critères.</p></Card>
      ) : (
        <div className="space-y-2">
          {entries.map((entry) => (
            <Card key={entry.id}>
              <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
                <span className="font-mono text-xs">{entry.action}</span>
                <span className="text-xs font-normal text-text-muted">
                  {entry.targetType}{entry.targetId !== null && ` #${entry.targetId}`}
                </span>
              </p>
              <p className="mt-1 text-xs text-text-secondary">
                Par @{entry.actorUsername} (#{entry.actorId}) · {formatDate(entry.createdAt)}
              </p>
              {(entry.beforeJson !== null || entry.afterJson !== null) && (
                <p className="mt-1 font-mono text-xs text-text-muted">
                  {entry.beforeJson ?? '—'} → {entry.afterJson ?? '—'}
                </p>
              )}
              {entry.reason !== null && entry.reason.length > 0 && (
                <p className="mt-1 text-xs text-text-secondary">Motif : {entry.reason}</p>
              )}
            </Card>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      <p className="text-xs text-text-muted">{formatCount(totalElements)} acte(s). Aucun secret n’y figure jamais.</p>
    </div>
  )
}
