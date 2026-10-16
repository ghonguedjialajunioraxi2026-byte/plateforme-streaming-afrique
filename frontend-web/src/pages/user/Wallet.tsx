import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge, Button, Card, EmptyState, Loader } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { paymentsApi, type CoinPackage } from '../../services/api/payments'
import { walletApi, type Wallet, type WalletMovement } from '../../services/api/wallet'
import { formatCoins, formatMoney } from '../../features/wallet/paymentLabels'
import { formatDate } from '../../utils/format'

/** Libellé lisible d'un mouvement (journal comptable backend). */
function movementLabel(kind: string): string {
  const key = kind.toLowerCase()
  if (key.includes('gift')) return key.includes('receiv') || key.includes('reçu') ? 'Cadeau reçu' : 'Cadeau envoyé'
  if (key.includes('subscription') || key.includes('abonn')) return 'Abonnement'
  if (key.includes('reward') || key.includes('live')) return 'Live rewards'
  if (key.includes('purchase') || key.includes('achat')) return 'Achat de coins'
  if (key.includes('payout') || key.includes('withdraw') || key.includes('retrait')) return 'Retrait'
  return kind.length > 0 ? kind : 'Mouvement'
}

/// Mon portefeuille (étape 14) : solde, packs à acheter, accès historique.
/// Hub — l'achat complet vit sur /wallet/acheter, le suivi sur
/// /wallet/paiement/:id, l'historique sur /wallet/transactions.
export default function Wallet() {
  const [wallet, setWallet] = useState<Wallet | null>(null)
  const [packs, setPacks] = useState<CoinPackage[]>([])
  const [movements, setMovements] = useState<WalletMovement[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [balance, catalogue, journal] = await Promise.all([
          walletApi.me(),
          paymentsApi.listPackages(),
          walletApi.transactions(0, 5).catch(() => null),
        ])
        if (!cancelled) {
          setWallet(balance)
          setPacks(catalogue.slice(0, 3))
          setMovements(journal?.content ?? [])
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Chargement impossible')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-label="Chargement du portefeuille">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Mon portefeuille</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
          <div className="mt-4">
            <Button variant="secondary" onClick={() => window.location.reload()}>
              Réessayer
            </Button>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      {/* ============ MOBILE — carte solde maquette ============ */}
      <section aria-label="Solde de coins" className="md:hidden">
        <div className="rounded-2xl border border-primary-500/30 bg-gradient-to-br from-primary-500/25 via-bg-surface to-bg-surface p-5 shadow-glow-primary">
          <p className="flex items-center gap-1.5 text-[13px] font-medium text-text-secondary">
            <Icon name="coin" className="h-4 w-4 text-primary-400" />
            Solde de coins
          </p>
          <p className="mt-1 font-display text-4xl font-bold tracking-tight text-text-primary" aria-live="polite">
            {wallet !== null ? wallet.balanceCoins.toLocaleString('fr-FR') : '—'}
          </p>
          {/* Pas d'équivalent XAF : le backend expose le solde en coins
              uniquement, sans taux de conversion officiel — aucun montant
              fictif affiché. */}
          {wallet !== null && wallet.reservedCoins > 0 && (
            <p className="mt-1 text-xs text-text-muted">
              dont {wallet.reservedCoins.toLocaleString('fr-FR')} réservés
            </p>
          )}
          <Link
            to="/wallet/acheter"
            className="mt-4 flex h-12 items-center justify-center rounded-xl bg-primary-500 text-[15px] font-bold text-text-inverse shadow-glow-primary transition-colors active:bg-primary-400"
          >
            Recharger
          </Link>
        </div>
        {/* Actions rapides maquette (3 icônes) — navigations réelles uniquement :
            pas d'envoi/reception pair-à-pair côté backend. */}
        <div className="mt-2 grid grid-cols-3 gap-2">
          <Link
            to="/wallet/acheter"
            className="flex min-h-11 flex-col items-center justify-center gap-1 rounded-xl border border-border-default bg-bg-surface px-2 py-2.5 text-xs font-semibold text-text-secondary transition-colors active:bg-bg-elevated active:text-text-primary"
          >
            <Icon name="plus" className="h-4 w-4" />
            Acheter
          </Link>
          <Link
            to="/wallet/transactions"
            className="flex min-h-11 flex-col items-center justify-center gap-1 rounded-xl border border-border-default bg-bg-surface px-2 py-2.5 text-xs font-semibold text-text-secondary transition-colors active:bg-bg-elevated active:text-text-primary"
          >
            <Icon name="clock" className="h-4 w-4" />
            Historique
          </Link>
          <Link
            to="/explorer"
            className="flex min-h-11 flex-col items-center justify-center gap-1 rounded-xl border border-border-default bg-bg-surface px-2 py-2.5 text-xs font-semibold text-text-secondary transition-colors active:bg-bg-elevated active:text-text-primary"
          >
            <Icon name="gift" className="h-4 w-4" />
            Soutenir
          </Link>
        </div>
      </section>

      {/* ============ MOBILE — transactions récentes ============ */}
      <section aria-label="Transactions récentes" className="md:hidden">
        <div className="mb-2 flex items-center justify-between">
          <h2 className="font-display text-[17px] font-bold tracking-tight text-text-primary">
            Transactions
          </h2>
          <Link
            to="/wallet/transactions"
            className="flex min-h-11 items-center text-[13px] font-semibold text-primary-400 active:text-primary-300"
          >
            Voir tout →
          </Link>
        </div>
        {movements.length === 0 ? (
          <EmptyState
            icon="wallet"
            title="Aucune transaction"
            description="Tes achats, cadeaux et gains apparaîtront ici."
          />
        ) : (
          <ul className="divide-y divide-border-default rounded-2xl border border-border-default bg-bg-surface">
            {movements.map((movement) => {
              const credit = movement.direction === 'CREDIT'
              return (
                <li key={movement.id} className="flex items-center gap-3 px-4 py-3">
                  <span
                    aria-hidden="true"
                    className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${credit ? 'bg-primary-500/15 text-primary-400' : 'bg-warning/15 text-warning'}`}
                  >
                    <Icon name={credit ? 'gift' : 'coin'} className="h-4 w-4" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-text-primary">
                      {movementLabel(movement.kind)}
                    </span>
                    <span className="block text-xs text-text-muted">
                      {formatDate(movement.createdAt)}
                    </span>
                  </span>
                  <span
                    className={`shrink-0 text-sm font-bold ${credit ? 'text-primary-400' : 'text-text-secondary'}`}
                  >
                    {credit ? '+' : '−'}{movement.amountCoins.toLocaleString('fr-FR')}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {/* ============ DESKTOP — inchangé ============ */}
      <div className="hidden flex-wrap items-center justify-between gap-3 md:flex">
        <h1 className="font-display text-2xl font-bold text-text-primary">Mon portefeuille</h1>
        <Badge variant="primary" dot={false}>
          {wallet !== null ? formatCoins(wallet.balanceCoins) : '—'}
        </Badge>
      </div>

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm text-text-secondary">Solde disponible</p>
            <p className="font-display text-4xl font-bold text-text-primary" aria-live="polite">
              {wallet !== null ? wallet.balanceCoins.toLocaleString('fr-FR') : '—'}
              <span className="ml-2 text-base font-medium text-text-secondary">coins</span>
            </p>
            {wallet !== null && wallet.reservedCoins > 0 && (
              <p className="mt-1 text-xs text-text-muted">
                dont {wallet.reservedCoins.toLocaleString('fr-FR')} réservés
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to="/wallet/acheter">
              <Button variant="primary">Acheter des coins</Button>
            </Link>
            <Link to="/wallet/transactions">
              <Button variant="secondary">Historique</Button>
            </Link>
          </div>
        </div>
      </Card>

      <section aria-labelledby="packs-title">
        <div className="mb-3 flex items-center justify-between">
          <h2 id="packs-title" className="font-display text-lg font-semibold text-text-primary">
            Acheter des coins
          </h2>
          <Link to="/wallet/acheter" className="text-sm text-primary-400 hover:underline">
            Tous les packs
          </Link>
        </div>
        {packs.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun pack disponible pour le moment.</p>
          </Card>
        ) : (
          <div className="grid gap-3 sm:grid-cols-3">
            {packs.map((pack) => (
              <Card key={pack.id}>
                <p className="font-semibold text-text-primary">{pack.name}</p>
                <p className="mt-1 font-display text-2xl font-bold text-primary-400">
                  {pack.coinsAmount.toLocaleString('fr-FR')}
                  <span className="ml-1 text-xs font-medium text-text-secondary">coins</span>
                </p>
                <p className="mt-1 text-sm text-text-secondary">
                  {formatMoney(pack.priceAmount, pack.priceCurrency)}
                </p>
                <div className="mt-3">
                  <Link to={`/wallet/acheter?pack=${pack.id}`}>
                    <Button variant="secondary" size="sm" fullWidth>
                      Acheter
                    </Button>
                  </Link>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
