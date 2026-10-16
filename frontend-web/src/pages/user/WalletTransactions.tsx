import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge, Button, Card, Loader, Tabs, type TabItem } from '../../components/ui'
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
import { walletApi, type WalletMovement } from '../../services/api/wallet'

type TabId = 'payments' | 'movements'

/// Historique (étape 14) : /wallet/transactions.
/// Deux journaux SÉPARÉS (§30) : paiements (argent réel) et mouvements
/// de coins (journal comptable) — reliés par la référence interne.
export default function WalletTransactions() {
  const [tab, setTab] = useState<TabId>('payments')
  const [payments, setPayments] = useState<Payment[]>([])
  const [paymentsTotal, setPaymentsTotal] = useState(0)
  const [paymentsPage, setPaymentsPage] = useState(0)
  const [movements, setMovements] = useState<WalletMovement[]>([])
  const [movementsTotal, setMovementsTotal] = useState(0)
  const [movementsPage, setMovementsPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadPayments = useCallback(async (page: number) => {
    const history = await paymentsApi.myHistory(page, 10)
    setPayments(history.content)
    setPaymentsTotal(history.totalElements)
    setPaymentsPage(history.page)
  }, [])

  const loadMovements = useCallback(async (page: number) => {
    const journal = await walletApi.transactions(page, 10)
    setMovements(journal.content)
    setMovementsTotal(journal.totalElements)
    setMovementsPage(journal.page)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await Promise.all([loadPayments(0), loadMovements(0)])
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
  }, [loadPayments, loadMovements])

  async function changePaymentsPage(next: number) {
    try {
      await loadPayments(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chargement impossible')
    }
  }

  async function changeMovementsPage(next: number) {
    try {
      await loadMovements(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chargement impossible')
    }
  }

  const tabs: TabItem[] = [
    { key: 'payments', label: `Paiements (${paymentsTotal})` },
    { key: 'movements', label: `Coins (${movementsTotal})` },
  ]

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <Link to="/wallet" className="text-sm text-primary-400 hover:underline">
        ← Portefeuille
      </Link>
      <h1 className="font-display text-2xl font-bold text-text-primary">Historique</h1>

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement de l'historique">
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
      ) : (
        <>
          <Tabs items={tabs} activeKey={tab} onChange={(key) => setTab(key as TabId)} />

          {tab === 'payments' && (
            <section aria-label="Historique des paiements">
              {payments.length === 0 ? (
                <Card>
                  <p className="text-sm text-text-secondary">Aucun paiement pour le moment.</p>
                </Card>
              ) : (
                <div className="space-y-3">
                  {payments.map((payment) => (
                    <Link key={payment.id} to={`/wallet/paiement/${payment.id}`}>
                      <Card className="transition-colors hover:border-border-hover">
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <div>
                            <p className="font-mono text-sm font-medium text-text-primary">
                              {payment.internalRef}
                            </p>
                            <p className="mt-0.5 text-xs text-text-muted">
                              {formatDate(payment.createdAt)} · {providerLabel(payment.provider)} ·{' '}
                              {methodLabel(payment.method)} · {formatCoins(payment.coinsAmount)}
                            </p>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="text-sm font-semibold text-text-primary">
                              {formatMoney(payment.fiatAmount, payment.fiatCurrency)}
                            </span>
                            <Badge variant={paymentStatusVariant(payment.status)}>
                              {paymentStatusLabel(payment.status)}
                            </Badge>
                          </div>
                        </div>
                      </Card>
                    </Link>
                  ))}
                  <Pagination
                    page={paymentsPage}
                    total={paymentsTotal}
                    size={10}
                    onChange={changePaymentsPage}
                  />
                </div>
              )}
            </section>
          )}

          {tab === 'movements' && (
            <section aria-label="Journal des coins">
              {movements.length === 0 ? (
                <Card>
                  <p className="text-sm text-text-secondary">Aucun mouvement de coins.</p>
                </Card>
              ) : (
                <div className="space-y-3">
                  {movements.map((movement) => (
                    <Card key={movement.id}>
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="text-sm font-medium text-text-primary">
                            {movement.kind === 'COIN_PURCHASE'
                              ? 'Achat de coins'
                              : movement.kind.replace(/_/g, ' ')}
                          </p>
                          <p className="mt-0.5 text-xs text-text-muted">
                            {formatDate(movement.createdAt)}
                            {movement.reference !== null ? ` · Réf. ${movement.reference}` : ''}
                            {` · Solde ${movement.balanceBefore.toLocaleString('fr-FR')} → ${movement.balanceAfter.toLocaleString('fr-FR')}`}
                          </p>
                        </div>
                        <span
                          className={
                            movement.direction === 'CREDIT'
                              ? 'text-sm font-bold text-live'
                              : 'text-sm font-bold text-text-primary'
                          }
                        >
                          {movement.direction === 'CREDIT' ? '+' : '−'}
                          {movement.amountCoins.toLocaleString('fr-FR')} coins
                        </span>
                      </div>
                    </Card>
                  ))}
                  <Pagination
                    page={movementsPage}
                    total={movementsTotal}
                    size={10}
                    onChange={changeMovementsPage}
                  />
                </div>
              )}
            </section>
          )}
        </>
      )}
    </div>
  )
}

function Pagination({
  page,
  total,
  size,
  onChange,
}: {
  page: number
  total: number
  size: number
  onChange: (page: number) => void
}) {
  const totalPages = Math.max(1, Math.ceil(total / size))
  if (totalPages <= 1) return null
  return (
    <nav className="flex items-center justify-between pt-1" aria-label="Pagination">
      <Button
        variant="secondary"
        size="sm"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        ← Précédent
      </Button>
      <span className="text-xs text-text-muted" aria-live="polite">
        Page {page + 1} / {totalPages} · {total} élément{total > 1 ? 's' : ''}
      </span>
      <Button
        variant="secondary"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Suivant →
      </Button>
    </nav>
  )
}
