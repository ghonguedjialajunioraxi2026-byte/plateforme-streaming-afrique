import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import {
  isPaymentPending,
  methodLabel,
  paymentStatusLabel,
  paymentStatusVariant,
  providerLabel,
  formatCoins,
  formatMoney,
} from '../../features/wallet/paymentLabels'
import { paymentsApi, type Payment } from '../../services/api/payments'
import { ApiError } from '../../services/http'

/// Suivi d'un paiement (étape 14) : /wallet/paiement/:id.
/// États : succès (coins crédités), attente (sondage), échec (motif).
/// Le backend reste la source de vérité (sondage GET, jamais d'optimisme).
export default function WalletPayment() {
  const { id } = useParams<{ id: string }>()
  const toast = useToast()
  const [payment, setPayment] = useState<Payment | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [simulating, setSimulating] = useState(false)

  const paymentId = Number(id)

  const refresh = useCallback(async () => {
    if (!Number.isFinite(paymentId)) {
      setError('Paiement introuvable')
      setLoading(false)
      return
    }
    try {
      const current = await paymentsApi.get(paymentId)
      setPayment(current)
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chargement impossible')
    } finally {
      setLoading(false)
    }
  }, [paymentId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  // Sondage tant que le paiement n'est pas terminal (toutes les 3 s).
  useEffect(() => {
    if (payment === null || !isPaymentPending(payment.status)) return
    const timer = window.setInterval(() => {
      void refresh()
    }, 3000)
    return () => window.clearInterval(timer)
  }, [payment, refresh])

  async function handleSimulate(outcome: 'SUCCEEDED' | 'FAILED' | 'PENDING') {
    if (payment === null) return
    setSimulating(true)
    try {
      await paymentsApi.simulate(payment.internalRef, outcome)
      await refresh()
      toast.show('Simulation appliquée', 'success')
    } catch (err) {
      if (err instanceof ApiError && err.status === 403) {
        toast.show('Simulation indisponible (sandbox désactivé)', 'error')
      } else {
        toast.show(err instanceof Error ? err.message : 'Simulation impossible', 'error')
      }
    } finally {
      setSimulating(false)
    }
  }

  async function handleCancel() {
    if (payment === null) return
    try {
      const cancelled = await paymentsApi.cancel(payment.id)
      setPayment(cancelled)
      toast.show('Paiement annulé', 'info')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Annulation impossible', 'error')
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-label="Chargement du paiement">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null || payment === null) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Paiement</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error ?? 'Paiement introuvable'}</p>
          <div className="mt-4">
            <Link to="/wallet">
              <Button variant="secondary">Retour au portefeuille</Button>
            </Link>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6">
      <Link to="/wallet" className="text-sm text-primary-400 hover:underline">
        ← Portefeuille
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-bold text-text-primary">Paiement</h1>
        <Badge variant={paymentStatusVariant(payment.status)}>
          {paymentStatusLabel(payment.status)}
        </Badge>
      </div>

      {payment.status === 'CONFIRMED' && (
        <Card className="border-live/40">
          <p className="font-display text-lg font-semibold text-text-primary" role="status">
            Paiement réussi
          </p>
          <p className="mt-1 text-sm text-text-secondary">
            {formatCoins(payment.coinsAmount)} ont été ajoutés à votre portefeuille.
          </p>
        </Card>
      )}

      {isPaymentPending(payment.status) && (
        <Card className="border-warning/40">
          <p className="font-display text-lg font-semibold text-text-primary" role="status">
            Paiement en cours de confirmation…
          </p>
          <p className="mt-1 text-sm text-text-secondary">
            {payment.paymentInstructions ?? 'Votre paiement est en cours de confirmation.'}
          </p>
          <div className="mt-3 flex items-center gap-2 text-sm text-text-muted" aria-live="polite">
            <Loader size="sm" /> Actualisation automatique
          </div>
        </Card>
      )}

      {payment.status === 'FAILED' && (
        <Card className="border-danger/40">
          <p className="font-display text-lg font-semibold text-text-primary" role="status">
            Le paiement n'a pas pu être finalisé.
          </p>
          {payment.failureReason !== null && (
            <p className="mt-1 text-sm text-text-secondary">{payment.failureReason}</p>
          )}
          <div className="mt-4">
            <Link to="/wallet/acheter">
              <Button variant="primary">Réessayer</Button>
            </Link>
          </div>
        </Card>
      )}

      {(payment.status === 'CANCELLED' || payment.status === 'EXPIRED') && (
        <Card>
          <p className="font-display text-lg font-semibold text-text-primary" role="status">
            {payment.status === 'CANCELLED' ? 'Paiement annulé.' : 'Paiement expiré.'}
          </p>
          <p className="mt-1 text-sm text-text-secondary">
            Aucun montant n'a été débité. Vous pouvez relancer un achat.
          </p>
          <div className="mt-4">
            <Link to="/wallet/acheter">
              <Button variant="primary">Nouvel achat</Button>
            </Link>
          </div>
        </Card>
      )}

      <Card>
        <dl className="space-y-1 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-text-secondary">Référence</dt>
            <dd className="font-mono font-medium text-text-primary">{payment.internalRef}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-secondary">Montant</dt>
            <dd className="font-medium text-text-primary">
              {formatMoney(payment.fiatAmount, payment.fiatCurrency)}
            </dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-secondary">Coins</dt>
            <dd className="font-medium text-text-primary">{formatCoins(payment.coinsAmount)}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-text-secondary">Moyen</dt>
            <dd className="font-medium text-text-primary">
              {providerLabel(payment.provider)} · {methodLabel(payment.method)}
            </dd>
          </div>
        </dl>
        {isPaymentPending(payment.status) && (
          <div className="mt-4">
            <Button variant="ghost" size="sm" onClick={handleCancel}>
              Annuler ce paiement
            </Button>
          </div>
        )}
      </Card>

      {isPaymentPending(payment.status) && (
        <Card>
          <p className="text-sm font-medium text-text-primary">Zone de test (sandbox)</p>
          <p className="mt-1 text-xs text-text-muted">
            Simule la réponse du prestataire via le pipeline webhook réel.
            Indisponible en production.
          </p>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button variant="secondary" size="sm" loading={simulating}
              onClick={() => handleSimulate('SUCCEEDED')}>
              Simuler le succès
            </Button>
            <Button variant="secondary" size="sm" loading={simulating}
              onClick={() => handleSimulate('FAILED')}>
              Simuler l'échec
            </Button>
          </div>
        </Card>
      )}
    </div>
  )
}
