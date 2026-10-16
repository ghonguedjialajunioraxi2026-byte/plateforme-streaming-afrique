import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Button, Card, Loader } from '../../components/ui'
import { useToast } from '../../components/ui'
import {
  methodLabel,
  providerLabel,
  formatCoins,
  formatMoney,
} from '../../features/wallet/paymentLabels'
import {
  newIdempotencyKey,
  paymentsApi,
  type CoinPackage,
  type PaymentProvider,
} from '../../services/api/payments'
import { profilesApi } from '../../services/api/profiles'
import { regionApi, type CountryPaymentMethod } from '../../services/api/region'
import { ApiError } from '../../services/http'

/// Achat de coins (étape 14) : pack → moyen → confirmation → création.
/// Le frontend n'envoie QUE l'intention ; prix/coins/devise relus serveur.
export default function WalletPurchase() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const toast = useToast()

  const [packs, setPacks] = useState<CoinPackage[]>([])
  const [providers, setProviders] = useState<PaymentProvider[]>([])
  const [countryMethods, setCountryMethods] = useState<CountryPaymentMethod[]>([])
  const [packCurrency, setPackCurrency] = useState<string | null>(null)
  const [selectedPackId, setSelectedPackId] = useState<number | null>(null)
  const [selectedProvider, setSelectedProvider] = useState('')
  const [selectedMethod, setSelectedMethod] = useState('')
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        // Devise préférée du profil (meilleur effort) → vitrine filtrée,
        // repli catalogue complet si aucun pack dans cette devise.
        const me = await profilesApi.getMe().catch(() => null)
        const currency = me?.preferredCurrency ?? null
        const country = me?.countryCode ?? null
        let catalogue = await paymentsApi.listPackages(currency ?? undefined)
        if (catalogue.length === 0 && currency) {
          catalogue = await paymentsApi.listPackages()
        }
        const available = await paymentsApi.listProviders()
        const methods = country
          ? await regionApi.paymentMethods(country).catch(() => [] as CountryPaymentMethod[])
          : []
        if (cancelled) return
        setPacks(catalogue)
        setCountryMethods(methods)
        setPackCurrency(currency && catalogue.length > 0 ? currency : null)
        setProviders(available)
        const preselected = Number(searchParams.get('pack'))
        if (Number.isFinite(preselected) && catalogue.some((p) => p.id === preselected)) {
          setSelectedPackId(preselected)
        } else if (catalogue.length > 0) {
          setSelectedPackId(catalogue[0]?.id ?? null)
        }
        if (available.length > 0) {
          const first = available[0]
          if (first) {
            setSelectedProvider(first.provider)
            setSelectedMethod(first.methods[0] ?? '')
          }
        }
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
  }, [searchParams])

  const selectedPack = useMemo(
    () => packs.find((p) => p.id === selectedPackId) ?? null,
    [packs, selectedPackId],
  )
  const providerMethods = useMemo(
    () => providers.find((p) => p.provider === selectedProvider)?.methods ?? [],
    [providers, selectedProvider],
  )
  const providerIsSandbox = useMemo(
    () => providers.find((p) => p.provider === selectedProvider)?.sandbox ?? false,
    [providers, selectedProvider],
  )

  function handleProviderChange(provider: string) {
    setSelectedProvider(provider)
    const methods = providers.find((p) => p.provider === provider)?.methods ?? []
    setSelectedMethod(methods[0] ?? '')
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (selectedPack === null || selectedProvider === '' || selectedMethod === '') return
    setSubmitting(true)
    try {
      const payment = await paymentsApi.create({
        packageId: selectedPack.id,
        provider: selectedProvider,
        method: selectedMethod,
        idempotencyKey: newIdempotencyKey(),
      })
      toast.show('Paiement initié', 'success')
      navigate(`/wallet/paiement/${payment.id}`)
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        toast.show('Trop de tentatives, réessayez dans quelques instants', 'error')
      } else {
        toast.show(err instanceof Error ? err.message : 'Paiement impossible', 'error')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-label="Chargement des packs">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Acheter des coins</h1>
      {packCurrency && (
        <p className="text-sm text-text-secondary">Prix affichés en {packCurrency} (devise de votre profil).</p>
      )}
      {countryMethods.length > 0 && (
        <Card>
          <h2 className="text-sm font-semibold text-text-primary">Moyens actifs dans votre pays</h2>
          <ul className="mt-1 space-y-1 text-sm text-text-secondary">
            {countryMethods.map((method) => (
              <li key={method.id}>
                {method.provider} · {method.method} · {method.currency}
                {method.sandbox ? ' (sandbox)' : ''}
              </li>
            ))}
          </ul>
          <p className="mt-1 text-xs text-text-secondary">
            Le paiement reste traité par les prestataires disponibles ci-dessous — prix et devise relus côté serveur.
          </p>
        </Card>
      )}
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
      <div className="flex items-center gap-3">
        <Link to="/wallet" className="text-sm text-primary-400 hover:underline">
          ← Portefeuille
        </Link>
      </div>
      <h1 className="font-display text-2xl font-bold text-text-primary">Acheter des coins</h1>

      <section aria-labelledby="pack-title">
        <h2 id="pack-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
          1. Choisir un pack
        </h2>
        {packs.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun pack disponible pour le moment.</p>
          </Card>
        ) : (
          <div className="grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Packs de coins">
            {packs.map((pack) => {
              const selected = pack.id === selectedPackId
              return (
                <Card
                  key={pack.id}
                  onClick={() => setSelectedPackId(pack.id)}
                  className={selected ? 'border-primary-500 shadow-glow-primary' : undefined}
                >
                  <div role="radio" aria-checked={selected} tabIndex={0}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') setSelectedPackId(pack.id)
                    }}>
                    <p className="font-semibold text-text-primary">{pack.name}</p>
                    <p className="mt-1 font-display text-2xl font-bold text-primary-400">
                      {pack.coinsAmount.toLocaleString('fr-FR')}
                      <span className="ml-1 text-xs font-medium text-text-secondary">coins</span>
                    </p>
                    <p className="mt-1 text-sm text-text-secondary">
                      {formatMoney(pack.priceAmount, pack.priceCurrency)}
                    </p>
                    {pack.description !== null && (
                      <p className="mt-1 text-xs text-text-muted">{pack.description}</p>
                    )}
                  </div>
                </Card>
              )
            })}
          </div>
        )}
      </section>

      <section aria-labelledby="method-title">
        <h2 id="method-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
          2. Choisir un moyen de paiement
        </h2>
        {providers.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun moyen de paiement disponible.</p>
          </Card>
        ) : (
          <Card>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-text-primary">Prestataire</span>
                <select
                  value={selectedProvider}
                  onChange={(e) => handleProviderChange(e.target.value)}
                  className="w-full rounded-lg border border-border-default bg-bg-primary px-4 py-3 text-text-primary outline-none focus:border-primary-500"
                >
                  {providers.map((p) => (
                    <option key={p.provider} value={p.provider}>
                      {providerLabel(p.provider)}{p.sandbox ? ' (test)' : ''}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block text-sm">
                <span className="mb-1 block font-medium text-text-primary">Moyen</span>
                <select
                  value={selectedMethod}
                  onChange={(e) => setSelectedMethod(e.target.value)}
                  className="w-full rounded-lg border border-border-default bg-bg-primary px-4 py-3 text-text-primary outline-none focus:border-primary-500"
                >
                  {providerMethods.map((m) => (
                    <option key={m} value={m}>
                      {methodLabel(m)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {providerIsSandbox && (
              <p className="mt-3 text-xs text-text-muted">
                Prestataire de test : aucun argent réel ne sera débité.
              </p>
            )}
          </Card>
        )}
      </section>

      {selectedPack !== null && selectedProvider !== '' && selectedMethod !== '' && (
        <section aria-labelledby="confirm-title">
          <h2 id="confirm-title" className="mb-3 font-display text-lg font-semibold text-text-primary">
            3. Confirmation
          </h2>
          <Card>
            <dl className="space-y-1 text-sm">
              <div className="flex justify-between">
                <dt className="text-text-secondary">Vous allez acheter</dt>
                <dd className="font-semibold text-text-primary">{formatCoins(selectedPack.coinsAmount)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">Prix</dt>
                <dd className="font-semibold text-text-primary">
                  {formatMoney(selectedPack.priceAmount, selectedPack.priceCurrency)}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-text-secondary">Moyen de paiement</dt>
                <dd className="font-semibold text-text-primary">
                  {providerLabel(selectedProvider)} · {methodLabel(selectedMethod)}
                </dd>
              </div>
            </dl>
            <form onSubmit={handleSubmit} className="mt-4">
              <Button type="submit" variant="primary" fullWidth loading={submitting}
                disabled={submitting}>
                Continuer vers le paiement
              </Button>
            </form>
          </Card>
        </section>
      )}
    </div>
  )
}
