import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Button, Card, EmptyState, Input, Loader, Tabs, useToast } from '../../components/ui'
import { Pagination } from '../../components/admin/Pagination'
import { usePageMeta } from '../../hooks/usePageMeta'
import {
  BETA_APP_VERSION,
  betaApi,
  type BetaFeedback,
  type BetaFeedbackStatus,
  type BetaInvitation,
  type BetaInvitationStatus,
  type BetaPriority,
  type BetaStats,
} from '../../services/api/beta'

const PAGE_SIZE = 20
const INVITE_TABS = [
  { key: 'PENDING', label: 'En attente' },
  { key: 'ACCEPTED', label: 'Acceptées' },
  { key: 'REVOKED', label: 'Révoquées' },
]
const FEEDBACK_TABS = [
  { key: 'OPEN', label: 'Ouverts' },
  { key: 'TRIAGED', label: 'Triés' },
  { key: 'IN_PROGRESS', label: 'En cours' },
  { key: 'FIXED', label: 'Corrigés' },
  { key: 'VERIFIED', label: 'Vérifiés' },
  { key: 'CLOSED', label: 'Fermés' },
]
const PRIORITIES: BetaPriority[] = ['P0', 'P1', 'P2', 'P3']

/// Pilotage de la bêta privée (étape 24) : /admin/beta.
/// Compteurs réels + invitations (code montré UNE fois à la création,
/// jamais relisible) + triage des retours (statut + priorité P0..P3).
/// Suspension/retrait d'un testeur : statuts existants (/admin/users).
export default function AdminBeta() {
  usePageMeta('Bêta privée', `Pilotage bêta ${BETA_APP_VERSION} — invitations et retours`)
  const toast = useToast()
  const [tab, setTab] = useState<'invitations' | 'feedback'>('invitations')
  const [stats, setStats] = useState<BetaStats | null>(null)

  const [inviteStatus, setInviteStatus] = useState<BetaInvitationStatus>('PENDING')
  const [invitations, setInvitations] = useState<BetaInvitation[]>([])
  const [invitePage, setInvitePage] = useState(0)
  const [invitePages, setInvitePages] = useState(0)
  const [inviteTotal, setInviteTotal] = useState(0)

  const [feedbackStatus, setFeedbackStatus] = useState<BetaFeedbackStatus>('OPEN')
  const [feedback, setFeedback] = useState<BetaFeedback[]>([])
  const [feedbackPage, setFeedbackPage] = useState(0)
  const [feedbackPages, setFeedbackPages] = useState(0)
  const [feedbackTotal, setFeedbackTotal] = useState(0)

  const [email, setEmail] = useState('')
  const [note, setNote] = useState('')
  const [inviting, setInviting] = useState(false)
  const [lastCode, setLastCode] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    const [statsResult, invites, items] = await Promise.all([
      betaApi.stats(),
      betaApi.invitations(inviteStatus, invitePage, PAGE_SIZE),
      betaApi.feedback(feedbackStatus, feedbackPage, PAGE_SIZE),
    ])
    setStats(statsResult)
    setInvitations(invites.content)
    setInvitePage(invites.page)
    setInvitePages(invites.totalPages)
    setInviteTotal(invites.totalElements)
    setFeedback(items.content)
    setFeedbackPage(items.page)
    setFeedbackPages(items.totalPages)
    setFeedbackTotal(items.totalElements)
  }, [inviteStatus, invitePage, feedbackStatus, feedbackPage])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload()
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
  }, [reload])

  function refresh(silent = false) {
    void reload().catch((err: unknown) => {
      if (!silent) toast.show(err instanceof Error ? err.message : 'Rechargement impossible', 'error')
    })
  }

  async function handleInvite(event: FormEvent) {
    event.preventDefault()
    if (email.trim() === '') return
    setInviting(true)
    try {
      const created = await betaApi.invite(email.trim(), note.trim() === '' ? undefined : note.trim())
      setLastCode(created.code)
      setEmail('')
      setNote('')
      toast.show(`Invitation créée pour ${created.email}`, 'success')
      refresh(true)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Invitation impossible', 'error')
    } finally {
      setInviting(false)
    }
  }

  async function handleRevoke(invitation: BetaInvitation) {
    try {
      await betaApi.revoke(invitation.id)
      toast.show('Invitation révoquée', 'success')
      refresh(true)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Révocation impossible', 'error')
    }
  }

  async function handleTriage(item: BetaFeedback, status: BetaFeedbackStatus, priority: BetaPriority | null) {
    try {
      await betaApi.triage(item.id, status, priority)
      toast.show('Retour trié', 'success')
      refresh(true)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Triage impossible', 'error')
    }
  }

  if (loading) return <Loader />
  if (error !== null) return <EmptyState title="Chargement impossible" description={error} />

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Bêta privée ({BETA_APP_VERSION})</h1>

      <div className="grid grid-cols-2 gap-2 md:grid-cols-5">
        <Card><div className="text-2xl font-bold">{stats?.invitations ?? 0}</div><div className="text-sm text-text-secondary">Invitations</div></Card>
        <Card><div className="text-2xl font-bold">{stats?.pending ?? 0}</div><div className="text-sm text-text-secondary">En attente</div></Card>
        <Card><div className="text-2xl font-bold">{stats?.accepted ?? 0}</div><div className="text-sm text-text-secondary">Acceptées</div></Card>
        <Card><div className="text-2xl font-bold">{stats?.feedback ?? 0}</div><div className="text-sm text-text-secondary">Retours</div></Card>
        <Card><div className="text-2xl font-bold">{stats?.feedbackOpen ?? 0}</div><div className="text-sm text-text-secondary">Retours ouverts</div></Card>
      </div>

      <Tabs
        items={[
          { key: 'invitations', label: 'Invitations' },
          { key: 'feedback', label: 'Retours' },
        ]}
        activeKey={tab}
        onChange={(key) => setTab(key as typeof tab)}
      />

      {tab === 'invitations' && (
        <div className="space-y-4">
          <Card>
            <form onSubmit={handleInvite} className="flex flex-wrap items-end gap-2" noValidate>
              <div className="min-w-[220px] flex-1">
                <Input label="Email invité" name="beta-invite-email" value={email}
                  onChange={(e) => setEmail(e.target.value)} placeholder="testeur@example.com" />
              </div>
              <div className="min-w-[220px] flex-1">
                <Input label="Note (profil testeur)" name="beta-invite-note" value={note}
                  onChange={(e) => setNote(e.target.value)} placeholder="Créateur gaming, Android…" />
              </div>
              <Button type="submit" variant="secondary" size="sm" loading={inviting}>Inviter</Button>
            </form>
            {lastCode !== null && (
              <p className="mt-2 rounded-lg bg-bg-elevated p-3 text-sm">
                Code à transmettre (affiché une seule fois) :{' '}
                <code className="font-mono font-bold">{lastCode}</code>
              </p>
            )}
          </Card>
          <Tabs
            items={INVITE_TABS}
            activeKey={inviteStatus}
            onChange={(key) => { setInviteStatus(key as BetaInvitationStatus); setInvitePage(0) }}
          />
          {invitations.length === 0 ? (
            <EmptyState title="Aucune invitation" description="Aucune invitation dans ce statut." />
          ) : (
            <div className="space-y-2">
              {invitations.map((invitation) => (
                <Card key={invitation.id}>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div>
                      <div className="font-medium">{invitation.email}</div>
                      <div className="text-sm text-text-secondary">
                        Expire le {new Date(invitation.expiresAt).toLocaleString()}
                        {invitation.acceptedUserId !== null && ` · compte #${invitation.acceptedUserId}`}
                      </div>
                    </div>
                    {invitation.status === 'PENDING' && (
                      <Button variant="secondary" size="sm" onClick={() => handleRevoke(invitation)}>
                        Révoquer
                      </Button>
                    )}
                  </div>
                </Card>
              ))}
              <Pagination page={invitePage} totalPages={invitePages} totalElements={inviteTotal} onPage={setInvitePage} />
            </div>
          )}
        </div>
      )}

      {tab === 'feedback' && (
        <div className="space-y-4">
          <Tabs
            items={FEEDBACK_TABS}
            activeKey={feedbackStatus}
            onChange={(key) => { setFeedbackStatus(key as BetaFeedbackStatus); setFeedbackPage(0) }}
          />
          {feedback.length === 0 ? (
            <EmptyState title="Aucun retour" description="Aucun retour dans ce statut." />
          ) : (
            <div className="space-y-2">
              {feedback.map((item) => (
                <Card key={item.id}>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-[220px] flex-1">
                      <div className="font-medium">[{item.category}] {item.title}</div>
                      <p className="mt-1 text-sm text-text-secondary">{item.description}</p>
                      <div className="mt-1 text-xs text-text-muted">
                        #{item.id} · compte #{item.reporterId} · {item.route ?? '—'} · {item.appVersion ?? '—'} · {item.device ?? '—'}
                        {item.priority !== null && ` · ${item.priority}`}
                      </div>
                    </div>
                    <TriageControls item={item} onTriage={handleTriage} />
                  </div>
                </Card>
              ))}
              <Pagination page={feedbackPage} totalPages={feedbackPages} totalElements={feedbackTotal} onPage={setFeedbackPage} />
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function TriageControls({
  item,
  onTriage,
}: {
  item: BetaFeedback
  onTriage: (item: BetaFeedback, status: BetaFeedbackStatus, priority: BetaPriority | null) => void
}) {
  const [status, setStatus] = useState<BetaFeedbackStatus>(item.status)
  const [priority, setPriority] = useState<BetaPriority | ''>(item.priority ?? '')
  return (
    <div className="flex flex-wrap items-end gap-2">
      <label className="block text-sm">
        <span className="mb-1 block font-medium text-text-secondary">Statut</span>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value as BetaFeedbackStatus)}
          className="rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
        >
          {(['OPEN', 'TRIAGED', 'IN_PROGRESS', 'FIXED', 'VERIFIED', 'CLOSED'] as BetaFeedbackStatus[]).map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </label>
      <label className="block text-sm">
        <span className="mb-1 block font-medium text-text-secondary">Priorité</span>
        <select
          value={priority}
          onChange={(e) => setPriority(e.target.value as BetaPriority | '')}
          className="rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
        >
          <option value="">—</option>
          {PRIORITIES.map((p) => <option key={p} value={p}>{p}</option>)}
        </select>
      </label>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => onTriage(item, status, priority === '' ? null : priority)}
      >
        Appliquer
      </Button>
    </div>
  )
}
