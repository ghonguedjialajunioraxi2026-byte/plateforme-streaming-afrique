import { useState, type FormEvent } from 'react'
import { Avatar, Badge, Button, Card, Input, useToast } from '../../components/ui'
import { formatCount, formatDate } from '../../utils/format'
import { moderationApi, type UserSummary } from '../../services/api/moderation'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Comptes côté modérateurs (étape 17) : /moderation/users.
/// Recherche par id : profil restreint (jamais d'email — réservé à l'ADMIN)
/// + sanctions. La gestion du compte (statut, rôle) reste /admin/users.
export default function ModUsers() {
  usePageMeta('Comptes', 'Dossiers de modération des comptes')
  const toast = useToast()
  const [userId, setUserId] = useState('')
  const [summary, setSummary] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (userId.trim() === '') return
    setLoading(true)
    setError(null)
    try {
      const result = await moderationApi.userSummary(Number(userId))
      setSummary(result)
    } catch (err) {
      setSummary(null)
      const message = err instanceof Error ? err.message : 'Recherche impossible'
      setError(message)
      toast.show(message, 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Comptes</h1>

      <Card>
        <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-[160px] flex-1">
            <Input
              label="Compte (id)"
              name="moduser-id"
              type="number"
              min={1}
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="ex. 9"
            />
          </div>
          <Button type="submit" variant="secondary" size="sm" loading={loading} disabled={loading}>
            Consulter le dossier
          </Button>
        </form>
      </Card>

      {error !== null && (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      )}

      {summary !== null && (
        <>
          <Card>
            <div className="flex items-center gap-3">
              <Avatar name={summary.displayName ?? summary.username} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-semibold text-text-primary">
                  @{summary.username}
                  <Badge variant="neutral">{summary.role}</Badge>
                  {summary.status === 'ACTIVE'
                    ? <Badge variant="accent">Actif</Badge>
                    : <Badge variant="danger">{summary.status}</Badge>}
                </p>
                <p className="mt-0.5 text-xs text-text-muted">
                  #{summary.id} · {summary.displayName ?? '—'} · inscrit le {formatDate(summary.createdAt)}
                </p>
              </div>
            </div>
          </Card>

          <section aria-label="Sanctions du compte">
            <h2 className="mb-2 font-display text-lg font-bold text-text-primary">
              Sanctions ({formatCount(summary.recentActions.length)})
            </h2>
            {summary.recentActions.length === 0 ? (
              <Card><p className="text-sm text-text-secondary">Aucune sanction enregistrée.</p></Card>
            ) : (
              <div className="space-y-2">
                {summary.recentActions.map((sanction) => (
                  <Card key={sanction.id}>
                    <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
                      #{sanction.id}
                      <Badge variant="neutral">{sanction.action}</Badge>
                      <Badge variant={sanction.state === 'ACTIVE' ? 'danger' : 'neutral'}>
                        {sanction.state}
                      </Badge>
                    </p>
                    <p className="mt-1 text-xs text-text-secondary">
                      {sanction.reason} · {formatDate(sanction.createdAt)}
                      {sanction.endsAt !== null && ` · jusqu’au ${formatDate(sanction.endsAt)}`}
                    </p>
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
