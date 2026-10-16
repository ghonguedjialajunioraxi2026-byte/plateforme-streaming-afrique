import { useEffect, useState, type FormEvent } from 'react'
import { Button, Input, Modal } from '../ui'

/// Confirmation d'action sensible (étape 17) : Ban, suppression, arrêt…
/// Motif obligatoire quand `requireReason` (dossier d'audit côté backend).
/// Remplace `window.confirm` : accessible (focus, Escape) et traçable.
export function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Confirmer',
  danger = false,
  requireReason = false,
  reasonLabel = 'Motif (obligatoire, versé au dossier)',
  loading = false,
  onClose,
  onConfirm,
}: {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  requireReason?: boolean
  reasonLabel?: string
  loading?: boolean
  onClose: () => void
  onConfirm: (reason: string) => void
}) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (open) {
      setReason('')
      setError(null)
    }
  }, [open ])

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (requireReason && reason.trim().length === 0) {
      setError('Le motif est obligatoire')
      return
    }
    onConfirm(reason.trim())
  }

  return (
    <Modal open={open} onClose={onClose} title={title}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <p className="text-sm text-text-secondary">{message}</p>
        {requireReason && (
          <Input
            label={reasonLabel}
            name="confirm-reason"
            placeholder="Expliquez la décision…"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={500}
          />
        )}
        {error !== null && (
          <p className="text-sm text-danger" role="alert">{error}</p>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            Annuler
          </Button>
          <Button
            type="submit"
            variant={danger ? 'danger' : 'primary'}
            size="sm"
            loading={loading}
            disabled={loading}
          >
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
