import { useEffect, useState, type FormEvent } from 'react'
import { Button, Card, Input, Loader, useToast } from '../../components/ui'
import { formatDate } from '../../utils/format'
import { adminApi, type AppConfig } from '../../services/api/admin'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Configuration plateforme (étape 17) : /admin/settings.
/// Valeurs courantes + nouvelle version (jamais d'écrasement destructeur,
/// historique complet côté backend, acte audité).
export default function AdminSettings() {
  usePageMeta('Configuration', 'Paramètres globaux de la plateforme')
  const toast = useToast()
  const [configs, setConfigs] = useState<AppConfig[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [key, setKey] = useState('')
  const [value, setValue] = useState('')
  const [saving, setSaving] = useState(false)

  async function reload() {
    const result = await adminApi.config()
    setConfigs(result.content)
  }

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        await reload()
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
  }, [])

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    if (key.trim().length === 0 || value.trim().length === 0) return
    setSaving(true)
    try {
      const saved = await adminApi.setConfig(key.trim(), value.trim())
      setConfigs((current) => {
        const others = current.filter((c) => c.key !== saved.key)
        return [...others, saved].sort((a, b) => a.key.localeCompare(b.key))
      })
      setKey('')
      setValue('')
      toast.show(`Paramètre enregistré (v${saved.version}) — acte audité`, 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Enregistrement impossible', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Configuration</h1>

      <Card>
        <p className="text-sm font-semibold text-text-primary">Nouveau réglage</p>
        <form onSubmit={handleSave} className="mt-3 space-y-3" noValidate>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <Input
              label="Clé (ex. features.gifts)"
              name="config-key"
              value={key}
              onChange={(e) => setKey(e.target.value)}
              maxLength={100}
            />
            <Input
              label="Valeur"
              name="config-value"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              maxLength={2000}
            />
          </div>
          <Button type="submit" variant="primary" size="sm" loading={saving} disabled={saving || key.trim() === '' || value.trim() === ''}>
            Enregistrer (nouvelle version)
          </Button>
        </form>
      </Card>

      {loading ? (
        <div className="flex min-h-[20vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : configs.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucun paramètre enregistré pour le moment.</p></Card>
      ) : (
        <Card className="overflow-x-auto p-0">
          <table className="w-full min-w-[520px] text-left text-sm">
            <thead>
              <tr className="border-b border-border text-xs uppercase text-text-muted">
                <th scope="col" className="px-4 py-2 font-medium">Clé</th>
                <th scope="col" className="px-4 py-2 font-medium">Valeur</th>
                <th scope="col" className="px-4 py-2 font-medium">Version</th>
                <th scope="col" className="px-4 py-2 font-medium">Effet</th>
              </tr>
            </thead>
            <tbody>
              {configs.map((config) => (
                <tr key={config.key} className="border-b border-border/50 last:border-0">
                  <td className="px-4 py-2 font-mono text-xs text-text-primary">{config.key}</td>
                  <td className="max-w-[280px] truncate px-4 py-2 font-mono text-xs text-text-secondary">{config.value}</td>
                  <td className="px-4 py-2 text-xs text-text-secondary">v{config.version}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-xs text-text-secondary">{formatDate(config.effectiveFrom)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}
