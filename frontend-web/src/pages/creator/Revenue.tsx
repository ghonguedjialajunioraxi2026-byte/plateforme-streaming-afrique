import { useEffect, useState } from 'react'
import { Badge, Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { formatCount, formatDate } from '../../utils/format'
import { creatorDashboardApi, type CreatorRevenue } from '../../services/api/dashboard'
import { usePageMeta } from '../../hooks/usePageMeta'

const KIND_LABELS: Record<string, string> = {
  COIN_PURCHASE: 'Achat de coins',
  GIFT_SEND: 'Cadeau envoyé',
  GIFT_RECEIPT: 'Cadeau reçu',
  FEE: 'Frais',
  SUBSCRIPTION_CHARGE: 'Abonnement prélevé',
  SUBSCRIPTION_PAYOUT: 'Abonnement reversé',
  WITHDRAWAL: 'Retrait',
  REFUND: 'Remboursement',
  AD_PAYOUT: 'Revenu publicitaire',
}

/// Revenus de la chaîne (étape 16) : /creator/revenue.
/// Coins uniquement (jamais mélangés à l'argent réel : chaque ligne garde
/// sa référence fiat d'origine). Le backend (journal des transactions)
/// est la seule source de vérité.
export default function Revenue() {
  usePageMeta('Revenus', 'Coins, sources et journal de votre chaîne')
  const toast = useToast()
  const [data, setData] = useState<CreatorRevenue | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const result = await creatorDashboardApi.revenue()
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Revenus inaccessibles', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Revenus</h1>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement des revenus">
          <Loader size="lg" />
        </div>
      )}

      {status === 'error' && (
        <Card>
          <p className="text-sm text-danger" role="alert">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      )}

      {status === 'ready' && data !== null && (
        <>
          <section aria-label="Soldes">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard
                label="Disponibles"
                value={data.wallet !== null ? formatCount(data.wallet.availableCoins) : '—'}
                hint="coins utilisables"
              />
              <StatCard
                label="En attente"
                value={data.wallet !== null ? formatCount(data.wallet.reservedCoins) : '—'}
                hint="mouvements réservés"
              />
              <StatCard label="Cadeaux reçus" value={formatCount(data.giftCoinsReceived)} hint={`${formatCount(data.giftsReceived)} envois`} />
              <StatCard label="Abonnements" value={formatCount(data.subscriptionCoinsReceived)} hint="reversés en coins" />
            </div>
            {data.wallet === null && (
              <Card>
                <p className="text-sm text-text-secondary">
                  Aucun portefeuille pour le moment — il sera créé automatiquement au premier
                  mouvement (cadeau reçu, abonnement).
                </p>
              </Card>
            )}
          </section>

          <section aria-label="Historique">
            <h2 className="mb-2 font-display text-lg font-bold text-text-primary">
              Mouvements récents <span className="text-sm font-normal text-text-muted">(total reçu : {formatCount(data.totalCoinsReceived)} coins)</span>
            </h2>
            {data.recentTransactions.length === 0 ? (
              <Card>
                <p className="text-sm text-text-secondary">Aucun mouvement pour le moment.</p>
              </Card>
            ) : (
              <Card className="overflow-x-auto p-0">
                <table className="w-full min-w-[560px] text-left text-sm">
                  <thead>
                    <tr className="border-b border-border text-xs uppercase text-text-muted">
                      <th scope="col" className="px-4 py-2 font-medium">Date</th>
                      <th scope="col" className="px-4 py-2 font-medium">Nature</th>
                      <th scope="col" className="px-4 py-2 text-right font-medium">Montant</th>
                      <th scope="col" className="px-4 py-2 font-medium">Statut</th>
                      <th scope="col" className="px-4 py-2 font-medium">Référence</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentTransactions.map((movement) => (
                      <tr key={movement.id} className="border-b border-border/50 last:border-0">
                        <td className="whitespace-nowrap px-4 py-2 text-xs text-text-secondary">
                          {formatDate(movement.createdAt)}
                        </td>
                        <td className="px-4 py-2 text-text-primary">
                          {KIND_LABELS[movement.kind] ?? movement.kind}
                        </td>
                        <td className={`whitespace-nowrap px-4 py-2 text-right font-semibold ${movement.direction === 'CREDIT' ? 'text-accent-400' : 'text-text-primary'}`}>
                          {movement.direction === 'CREDIT' ? '+' : '−'}{formatCount(movement.amountCoins)}
                          {movement.fiatCurrency !== null && (
                            <span className="ml-1 text-xs font-normal text-text-muted">
                              ({movement.fiatReferenceAmount} {movement.fiatCurrency})
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-2">
                          <Badge variant={movement.status === 'SUCCESS' ? 'accent' : 'warning'}>
                            {movement.status}
                          </Badge>
                        </td>
                        <td className="max-w-[180px] truncate px-4 py-2 font-mono text-xs text-text-muted">
                          {movement.reference ?? '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </Card>
            )}
          </section>
        </>
      )}
    </div>
  )
}
