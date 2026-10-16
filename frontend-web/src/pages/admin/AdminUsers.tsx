import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Avatar, Badge, Button, Card, Input, Loader, useToast } from '../../components/ui'
import { ConfirmModal } from '../../components/admin/ConfirmModal'
import { Pagination } from '../../components/admin/Pagination'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate } from '../../utils/format'
import { adminApi, type AdminUser, type UserRole, type UserStatus } from '../../services/api/admin'
import { usePageMeta } from '../../hooks/usePageMeta'

const PAGE_SIZE = 20
const ROLES: UserRole[] = ['USER', 'CREATOR', 'MODERATOR', 'ADMIN']
const STATUSES: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'CLOSED']

function statusBadge(status: UserStatus) {
  if (status === 'ACTIVE') return <Badge variant="accent">Actif</Badge>
  if (status === 'SUSPENDED') return <Badge variant="danger">Suspendu</Badge>
  return <Badge variant="neutral">Fermé</Badge>
}

/// Gestion des utilisateurs (étape 17) : /admin/users.
/// Recherche (email, pseudo, nom) + filtres rôle/statut + pagination.
/// Suspension / réactivation / fermeture motivées (auditées) + changement
/// de rôle (jamais sur soi-même ni sur un ADMIN — gardes backend).
export default function AdminUsers() {
  usePageMeta('Utilisateurs', 'Gestion des comptes de la plateforme')
  const toast = useToast()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<{ user: AdminUser; status: UserStatus } | null>(null)
  const [roleTarget, setRoleTarget] = useState<{ user: AdminUser; role: UserRole } | null>(null)
  const [acting, setActing] = useState(false)

  const reload = useCallback(async (nextPage: number, query: string, nextRole: UserRole | '', nextStatus: UserStatus | '') => {
    const result = await adminApi.users({
      search: query === '' ? undefined : query,
      role: nextRole === '' ? undefined : nextRole,
      status: nextStatus === '' ? undefined : nextStatus,
      page: nextPage,
      size: PAGE_SIZE,
    })
    setUsers(result.content)
    setPage(result.page)
    setTotalPages(result.totalPages)
    setTotalElements(result.totalElements)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload(page, appliedSearch, role, statusFilter)
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
  }, [reload, page, appliedSearch, role, statusFilter])

  function handleSearch(event: FormEvent) {
    event.preventDefault()
    setPage(0)
    setAppliedSearch(search.trim())
  }

  async function handleStatus(reason: string) {
    if (confirm === null) return
    setActing(true)
    try {
      const updated = await adminApi.setUserStatus(confirm.user.id, confirm.status, reason)
      setUsers((current) => current.map((u) => (u.id === updated.id ? updated : u)))
      setConfirm(null)
      toast.show('Statut modifié — acte audité', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  async function handleRole() {
    if (roleTarget === null) return
    setActing(true)
    try {
      const updated = await adminApi.setUserRole(roleTarget.user.id, roleTarget.role)
      setUsers((current) => current.map((u) => (u.id === updated.id ? { ...u, role: updated.role } : u)))
      setRoleTarget(null)
      toast.show('Rôle modifié', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Utilisateurs</h1>

      <Card>
        <form onSubmit={handleSearch} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-[200px] flex-1">
            <Input
              label="Recherche (email, pseudo, nom)"
              name="admin-user-search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="ada@example.com"
            />
          </div>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Rôle</span>
            <select
              value={role}
              onChange={(e) => { setRole(e.target.value as UserRole | ''); setPage(0) }}
              className="rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
            >
              <option value="">Tous</option>
              {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Statut</span>
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value as UserStatus | ''); setPage(0) }}
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
      ) : users.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucun compte pour ces critères.</p></Card>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase text-text-muted">
                <th scope="col" className="px-4 py-2 font-medium">Compte</th>
                <th scope="col" className="px-4 py-2 font-medium">Email</th>
                <th scope="col" className="px-4 py-2 font-medium">Rôle</th>
                <th scope="col" className="px-4 py-2 font-medium">Statut</th>
                <th scope="col" className="px-4 py-2 font-medium">Inscrit le</th>
                <th scope="col" className="px-4 py-2 font-medium"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className="border-b border-border/50 last:border-0">
                  <td className="px-4 py-2">
                    <div className="flex items-center gap-2">
                      <Avatar src={mediaUrl(user.avatarUrl) ?? undefined} name={user.displayName ?? user.username} size="sm" />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-text-primary">@{user.username}</p>
                        <p className="truncate text-xs text-text-muted">#{user.id} · {user.displayName ?? '—'}</p>
                      </div>
                    </div>
                  </td>
                  <td className="max-w-[200px] truncate px-4 py-2 text-xs text-text-secondary">{user.email}</td>
                  <td className="px-4 py-2">
                    <select
                      value={user.role}
                      aria-label={`Rôle de ${user.username}`}
                      onChange={(e) => setRoleTarget({ user, role: e.target.value as UserRole })}
                      className="rounded-lg border border-border bg-bg-elevated px-2 py-1 text-xs text-text-primary"
                    >
                      {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                    </select>
                  </td>
                  <td className="px-4 py-2">{statusBadge(user.status)}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-xs text-text-secondary">{formatDate(user.createdAt)}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-right">
                    {user.status === 'ACTIVE' ? (
                      <Button variant="ghost" size="sm" onClick={() => setConfirm({ user, status: 'SUSPENDED' })}>
                        Suspendre
                      </Button>
                    ) : (
                      <Button variant="ghost" size="sm" onClick={() => setConfirm({ user, status: 'ACTIVE' })}>
                        Réactiver
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <Pagination page={page} totalPages={totalPages} totalElements={totalElements} onPage={setPage} />
      <p className="text-xs text-text-muted">
        {formatCount(totalElements)} compte(s). Suspensions et changements de rôle : motif audité,
        compte notifié. Jamais sur soi-même ni sur un ADMIN (gardes backend).
      </p>

      <ConfirmModal
        open={confirm !== null}
        title={confirm !== null && confirm.status === 'ACTIVE' ? `Réactiver @${confirm.user.username} ?` : `Suspendre @${confirm?.user.username ?? ''} ?`}
        message={confirm !== null && confirm.status === 'ACTIVE'
          ? 'Le compte retrouvera l’accès complet à la plateforme.'
          : 'Le compte perdra immédiatement l’accès (connexion et JWT rejetés).'}
        confirmLabel={confirm?.status === 'ACTIVE' ? 'Réactiver' : 'Suspendre'}
        danger={confirm?.status !== 'ACTIVE'}
        requireReason
        loading={acting}
        onClose={() => setConfirm(null)}
        onConfirm={handleStatus}
      />

      <ConfirmModal
        open={roleTarget !== null}
        title={`Rôle de @${roleTarget?.user.username ?? ''}`}
        message={`Passer ce compte au rôle ${roleTarget?.role} ? Un MODERATOR ne peut pas promouvoir en ADMIN.`}
        confirmLabel="Changer le rôle"
        loading={acting}
        onClose={() => setRoleTarget(null)}
        onConfirm={() => handleRole()}
      />
    </div>
  )
}
