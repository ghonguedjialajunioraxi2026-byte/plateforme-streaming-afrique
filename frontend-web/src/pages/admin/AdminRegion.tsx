import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { regionApi, type Country, type CountryPaymentMethod, type RegionConfig } from '../../services/api/region'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Administration Africa+ (étape 19) : /admin/region.
/// Pays (activation), moyens de paiement par pays (préparation /
/// activation — exige une intégration officielle), flags `features.*`
/// via /admin/settings. Aucun secret ici.
export default function AdminRegion() {
  usePageMeta('Région Africa+', 'Configuration des marchés africains')
  const toast = useToast()
  const [countries, setCountries] = useState<Country[]>([])
  const [selected, setSelected] = useState('')
  const [methods, setMethods] = useState<CountryPaymentMethod[]>([])
  const [config, setConfig] = useState<RegionConfig | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [list, regionConfig] = await Promise.all([
        regionApi.countries(),
        regionApi.config(),
      ])
      setCountries(list)
      setConfig(regionConfig)
      if (!selected && list.length > 0) setSelected(list[0].code)
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Chargement impossible', 'error')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!selected) return
    regionApi.allPaymentMethods(selected).then(setMethods).catch(() => setMethods([]))
  }, [selected])

  async function toggleCountry(code: string, active: boolean) {
    try {
      const updated = await regionApi.setCountryActive(code, !active)
      setCountries((current) => current.map((c) => (c.code === code ? updated : c)))
      toast.show(`Pays ${updated.active ? 'activé' : 'désactivé'}`, 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Échec', 'error')
    }
  }

  async function toggleMethod(id: number, active: boolean) {
    try {
      const updated = await regionApi.setMethodActive(id, !active)
      setMethods((current) => current.map((m) => (m.id === id ? updated : m)))
      toast.show(`Moyen ${updated.active ? 'activé' : 'désactivé'}`, 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Échec', 'error')
    }
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-6xl p-6">
        <Loader />
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-text-primary">Région Africa+</h1>
        <p className="mt-1 text-sm text-text-secondary">
          Marchés, devises, langues et moyens de paiement — configurables, jamais hard-codés.
        </p>
      </div>

      <Card>
        <h2 className="text-sm font-semibold text-text-primary">Pays ({countries.length})</h2>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="text-xs text-text-secondary">
                <th className="py-2 pr-4">Code</th>
                <th className="py-2 pr-4">Nom</th>
                <th className="py-2 pr-4">Région</th>
                <th className="py-2 pr-4">Devise</th>
                <th className="py-2 pr-4">Locale</th>
                <th className="py-2 pr-4">Fuseau</th>
                <th className="py-2 pr-4">Statut</th>
                <th className="py-2">Action</th>
              </tr>
            </thead>
            <tbody>
              {countries.map((country) => (
                <tr key={country.code} className="border-t border-border">
                  <td className="py-2 pr-4 font-medium text-text-primary">{country.code}</td>
                  <td className="py-2 pr-4">
                    <button type="button" className="text-primary-400 hover:underline" onClick={() => setSelected(country.code)}>
                      {country.name}
                    </button>
                  </td>
                  <td className="py-2 pr-4 text-text-secondary">{country.region}</td>
                  <td className="py-2 pr-4 text-text-secondary">{country.defaultCurrency}</td>
                  <td className="py-2 pr-4 text-text-secondary">{country.defaultLocale}</td>
                  <td className="py-2 pr-4 text-text-secondary">{country.timezone}</td>
                  <td className="py-2 pr-4">
                    {country.active ? <Badge variant="accent">Actif</Badge> : <Badge variant="danger">Inactif</Badge>}
                  </td>
                  <td className="py-2">
                    <Button variant="secondary" size="sm" onClick={() => void toggleCountry(country.code, country.active)}>
                      {country.active ? 'Désactiver' : 'Activer'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <h2 className="text-sm font-semibold text-text-primary">
          Moyens de paiement — {selected || '—'}
        </h2>
        {methods.length === 0 ? (
          <p className="mt-1 text-sm text-text-secondary">Aucun moyen configuré pour ce pays.</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {methods.map((method) => (
              <li key={method.id} className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium text-text-primary">{method.provider}</span>
                <span className="text-text-secondary">{method.method} · {method.currency}</span>
                {method.active ? <Badge variant="accent">Actif</Badge> : <Badge variant="neutral">Préparé</Badge>}
                {method.sandbox && <Badge variant="neutral">sandbox</Badge>}
                <Button variant="secondary" size="sm" onClick={() => void toggleMethod(method.id, method.active)}>
                  {method.active ? 'Désactiver' : 'Activer'}
                </Button>
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs text-text-secondary">
          L'activation exige une intégration officielle câblée (API, webhooks, signature) — jamais de simulation.
        </p>
      </Card>

      <Card>
        <h2 className="text-sm font-semibold text-text-primary">Configuration</h2>
        <p className="mt-1 text-sm text-text-secondary">
          Devises : {(config?.activeCountries.length ?? 0) > 0 ? 'voir /currencies' : '—'} · Taux de change :{' '}
          {config?.exchangeRatesAvailable ? <Badge variant="accent">source branchée</Badge> : <Badge variant="neutral">aucune source — conversions refusées</Badge>}
        </p>
        {config && (
          <ul className="mt-2 space-y-1 text-sm text-text-secondary">
            {Object.entries(config.features).map(([flag, on]) => (
              <li key={flag}>
                <code>{flag}</code> : {on ? <Badge variant="accent">on</Badge> : <Badge variant="danger">off</Badge>}
              </li>
            ))}
          </ul>
        )}
        <p className="mt-2 text-xs text-text-secondary">
          Flags via <Link to="/admin/settings" className="text-primary-400 hover:underline">Configuration</Link>
          {' '}(clés <code>features.*</code>).
        </p>
      </Card>
    </div>
  )
}
