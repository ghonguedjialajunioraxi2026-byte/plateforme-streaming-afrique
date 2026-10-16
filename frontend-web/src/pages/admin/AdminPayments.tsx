import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Loader } from '../../components/ui'
import {
  methodLabel,
  paymentStatusLabel,
  paymentStatusVariant,
  providerLabel,
  formatCoins,
  formatDate,
  formatMoney,
} from '../../features/wallet/paymentLabels'
import { paymentsApi, type Payment } from '../../services/api/payments'

/// Supervision des paiements (étape 14, §42) — ADMIN.
/// Lecture seule : montant, devise, fournisseur, référence, statut, date.
/// JAMAIS de modification de l'historique financier.
export default function AdminPayments() {
  const [payments, setPayments] = useState<Payment[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (next: number) => {
    const result = await paymentsApi.adminAll(next, 20)
    setPayments(result.content)
    setTotal(result.totalElements)
    setPage(result.page)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function init() {
      try {
        await load(0)
        if (!cancelled) setError(null)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Chargement impossible')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void init()
    return () => {
      cancelled = true
    }
  }, [load])

  async function changePage(next: number) {
    try {
      await load(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chargement impossible')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Paiements</h1>
      <p className="text-sm text-text-secondary">
        Achats de Coins et transactions de la plateforme — supervision en lecture seule.
      </p>

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement des paiements">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
          <div className="mt-4">
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Réessayer
            </Button>
          </div>
        </Card>
      ) : payments.length === 0 ? (
        <Card>
          <p className="text-sm text-text-secondary">Aucun paiement enregistré.</p>
        </Card>
      ) : (
        <>
          <Card noPadding>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border-default text-xs uppercase text-text-muted">
                    <th scope="col" className="px-4 py-3">Référence</th>
                    <th scope="col" className="px-4 py-3">Date</th>
                    <th scope="col" className="px-4 py-3">Montant</th>
                    <th scope="col" className="px-4 py-3">Coins</th>
                    <th scope="col" className="px-4 py-3">Fournisseur</th>
                    <th scope="col" className="px-4 py-3">Statut</th>
                  </tr>
                </thead>
                <tbody>
                  {payments.map((payment) => (
                    <tr key={payment.id} className="border-b border-border-default last:border-0">
                      <td className="px-4 py-3 font-mono text-xs text-text-primary">
                        {payment.internalRef}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {formatDate(payment.createdAt)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 font-semibold text-text-primary">
                        {formatMoney(payment.fiatAmount, payment.fiatCurrency)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {formatCoins(payment.coinsAmount)}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {providerLabel(payment.provider)} · {methodLabel(payment.method)}
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={paymentStatusVariant(payment.status)}>
                          {paymentStatusLabel(payment.status)}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          {totalPages > 1 && (
            <nav className="flex items-center justify-between" aria-label="Pagination">
              <Button variant="secondary" size="sm" disabled={page <= 0}
                onClick={() => changePage(page - 1)}>
                ← Précédent
              </Button>
              <span className="text-xs text-text-muted" aria-live="polite">
                Page {page + 1} / {totalPages} · {total} paiement{total > 1 ? 's' : ''}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages}
                onClick={() => changePage(page + 1)}>
                Suivant →
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
