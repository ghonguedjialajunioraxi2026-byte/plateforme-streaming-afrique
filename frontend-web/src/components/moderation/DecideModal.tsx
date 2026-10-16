import { useEffect, useState, type FormEvent } from 'react'
import { Button, Input, Modal } from '../ui'
import type { SanctionAction, SanctionScope } from '../../services/api/moderation'
import { reportTargetLabel } from './ReportCard'
import type { Report } from '../../services/api/moderation'

const ACTIONS: { value: SanctionAction; label: string; hint: string }[] = [
  { value: 'DISMISS', label: 'Classer', hint: 'Aucun effet, acte seul' },
  { value: 'WARN', label: 'Avertir', hint: 'Notification au compte visé' },
  { value: 'HIDE_CONTENT', label: 'Masquer le contenu', hint: 'Message, VOD, clip, direct, chaîne' },
  { value: 'TIMEOUT', label: 'Exclure temporairement', hint: 'Compte suspendu jusqu’à échéance' },
  { value: 'BAN_TEMP', label: 'Bannir temporairement', hint: 'Compte suspendu jusqu’à échéance' },
  { value: 'BAN_PERM', label: 'Bannir définitivement', hint: 'Compte suspendu sans échéance' },
  { value: 'SUSPEND_CHAIN', label: 'Suspendre la chaîne', hint: 'Chaîne cible ou du compte visé' },
]

const SCOPES: SanctionScope[] = ['MESSAGE', 'CONTENT', 'CHAIN', 'ACCOUNT', 'PLATFORM']

/// Sanction d'un signalement (étape 17) : action, périmètre, compte visé
/// (requis pour TIMEOUT/bannissements), motif obligatoire, échéance pour
/// les mesures temporaires. Effets exécutés côté backend + audit.
export function DecideModal({
  open,
  report,
  loading = false,
  onClose,
  onConfirm,
}: {
  open: boolean
  report: Report | null
  loading?: boolean
  onClose: () => void
  onConfirm: (input: {
    action: SanctionAction
    scope: SanctionScope
    targetUserId: number | null
    reason: string
    endsAt: string | null
  }) => void
}) {
  const [action, setAction] = useState<SanctionAction>('WARN')
  const [scope, setScope] = useState<SanctionScope>('ACCOUNT')
  const [targetUserId, setTargetUserId] = useState('')
  const [reason, setReason] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      setAction('WARN')
      setScope('ACCOUNT')
      setTargetUserId('')
      setReason('')
      setEndsAt('')
      setError(null)
    }
  }, [open ])

  const needsTarget = action === 'TIMEOUT' || action === 'BAN_TEMP' || action === 'BAN_PERM'
  const needsEndsAt = action === 'TIMEOUT' || action === 'BAN_TEMP'

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (reason.trim().length === 0) {
      setError('Le motif est obligatoire')
      return
    }
    if (needsTarget && targetUserId.trim() === '') {
      setError('Cette sanction exige un compte visé (id)')
      return
    }
    if (needsEndsAt && endsAt === '') {
      setError('Cette sanction exige une échéance')
      return
    }
    onConfirm({
      action,
      scope,
      targetUserId: targetUserId.trim() === '' ? null : Number(targetUserId),
      reason: reason.trim(),
      endsAt: endsAt === '' ? null : new Date(endsAt).toISOString(),
    })
  }

  const selectClass = 'w-full rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary'

  return (
    <Modal open={open} onClose={onClose} title={report !== null ? `Sanction — signalement #${report.id} (${reportTargetLabel(report)})` : 'Sanction'}>
      <form onSubmit={handleSubmit} className="space-y-3" noValidate>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-text-secondary">Action</span>
          <select value={action} onChange={(e) => setAction(e.target.value as SanctionAction)} className={selectClass}>
            {ACTIONS.map((a) => <option key={a.value} value={a.value}>{a.label} — {a.hint}</option>)}
          </select>
        </label>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Périmètre</span>
            <select value={scope} onChange={(e) => setScope(e.target.value as SanctionScope)} className={selectClass}>
              {SCOPES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <Input
            label="Compte visé (id, si sanction de compte)"
            name="sanction-target"
            type="number"
            min={1}
            value={targetUserId}
            onChange={(e) => setTargetUserId(e.target.value)}
            placeholder="ex. 9"
          />
        </div>
        <Input
          label="Échéance (mesures temporaires)"
          name="sanction-ends-at"
          type="datetime-local"
          value={endsAt}
          onChange={(e) => setEndsAt(e.target.value)}
        />
        <Input
          label="Motif (obligatoire, versé au dossier)"
          name="sanction-reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          maxLength={500}
          placeholder="Expliquez la décision…"
        />
        {error !== null && <p className="text-sm text-danger" role="alert">{error}</p>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>Annuler</Button>
          <Button type="submit" variant="danger" size="sm" loading={loading} disabled={loading}>
            Appliquer la sanction
          </Button>
        </div>
      </form>
    </Modal>
  )
}
