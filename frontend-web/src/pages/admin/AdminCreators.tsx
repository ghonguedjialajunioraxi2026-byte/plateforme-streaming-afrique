import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { Avatar, Badge, Button, Card, Input, Loader, useToast } from '../../components/ui'
import { ConfirmModal } from '../../components/admin/ConfirmModal'
import { Pagination } from '../../components/admin/Pagination'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate } from '../../utils/format'
import { adminApi, type ChannelStatus } from '../../services/api/admin'
import type { ChannelDetail } from '../../types/domain'
import { usePageMeta } from '../../hooks/usePageMeta'

const PAGE_SIZE = 20
const STATUSES: ChannelStatus[] = ['ACTIVE', 'SUSPENDED', 'CLOSED']

function statusBadge(status: ChannelStatus) {
  if (status === 'ACTIVE') return <Badge variant="accent">Active</Badge>
  if (status === 'SUSPENDED') return <Badge variant="danger">Suspendue</Badge>
  return <Badge variant="neutral">Fermée</Badge>
}

/// Gestion des créateurs (étape 17) : /admin/creators.
/// Recherche + statut (CLOSED incluses) + suspension / réactivation
/// motivées (auditées). La fermeture reste au propriétaire (DELETE /creators).
export default function AdminCreators() {
  usePageMeta('Chaînes', 'Gestion des chaînes de la plateforme')
  const toast = useToast()
  const [channels, setChannels] = useState<ChannelDetail[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<ChannelStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<{ channel: ChannelDetail; suspend: boolean } | null>(null)
  const [acting, setActing] = useState(false)

  const reload = useCallback(async (nextPage: number, query: string, nextStatus: ChannelStatus | '') => {
    const result = await adminApi.creators({
      search: query === '' ? undefined : query,
      status: nextStatus === '' ? undefined : nextStatus,
      page: nextPage,
      size: PAGE_SIZE,
    })
    setChannels(result.content)
    setPage(result.page)
    setTotalPages(result.totalPages)
    setTotalElements(result.totalElements)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload(page, appliedSearch, statusFilter)
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
  }, [reload, page, appliedSearch, statusFilter])

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedSearch(search.trim())
  }

  async function handleConfirm(reason: string) {
    if (confirm === null) return
    setActing(true)
    try {
      const updated = confirm.suspend
        ? await adminApi.suspendCreator(confirm.channel.id, reason)
        : await adminApi.reactivateCreator(confirm.channel.id, reason)
      setChannels((current) => current.map((c) => (c.id === updated.id ? { ...c, status: updated.status } : c)))
      setConfirm(null)
      toast.show(confirm.suspend ? 'Chaîne suspendue — acte audité' : 'Chaîne réactivée', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Chaînes</h1>

      <Card>
        <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-[200px] flex-1">
            <Input
              label="Recherche (nom, slug)"
              name="admin-channel-search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="factu-congo"
            />
          </div>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Statut</span>
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value as ChannelStatus | ''); setPage(0) }}
              className="rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
            >
              <option value="">Tous</option>
              {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <Button type="submit" variant="secondary" size="sm">Rechercher</Button>
        </form>
      </Card>

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : channels.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucune chaîne pour ces critères.</p></Card>
      ) : (
        <div className="space-y-2">
          {channels.map((channel) => (
            <Card key={channel.id}>
              <div className="flex flex-wrap items-center gap-3">
                <Avatar src={mediaUrl(channel.avatarUrl) ?? undefined} name={channel.displayName} size="sm" />
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 truncate font-semibold text-text-primary">
                    <Link to={`/channel/${channel.slug}`} className="hover:underline">{channel.displayName}</Link>
                    {statusBadge(channel.status as ChannelStatus)}
                  </p>
                  <p className="mt-0.5 text-xs text-text-muted">
                    @{channel.slug} · {formatCount(channel.followerCount)} followers · depuis {formatDate(channel.createdAt)}
                  </p>
                </div>
                {channel.status === 'ACTIVE' ? (
                  <Button variant="ghost" size="sm" onClick={() => setConfirm({ channel, suspend: true })}>
                    Suspendre
                  </Button>
                ) : channel.status === 'SUSPENDED' ? (
                  <Button variant="ghost" size="sm" onClick={() => setConfirm({ channel, suspend: false })}>
                    Réactiver
                  </Button>
                ) : null}
              </div>
            </Card>
          ))}
        </div>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      <p className="text-xs text-text-muted">{formatCount(totalElements)} chaîne(s).</p>

      <ConfirmModal
        open={confirm !== null}
        title={confirm?.suspend === true ? `Suspendre « ${confirm?.channel.displayName ?? ''} » ?` : `Réactiver « ${confirm?.channel.displayName ?? ''} » ?`}
        message={confirm?.suspend === true
          ? 'La chaîne disparaîtra du public (contenus intacts, réactivation possible).'
          : 'La chaîne redeviendra visible en public.'}
        confirmLabel={confirm?.suspend === true ? 'Suspendre' : 'Réactiver'}
        danger={confirm?.suspend === true}
        requireReason
        loading={acting}
        onClose={() => setConfirm(null)}
        onConfirm={handleConfirm}
      />
    </div>
  )
}
