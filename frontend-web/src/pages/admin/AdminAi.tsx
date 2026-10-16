import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge, Button, Card, Loader } from '../../components/ui'
import { aiApi, type AiUsageStats } from '../../services/api/ai'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Monitoring IA (étape 18) : /admin/ai.
/// Provider, modèle, requêtes/tokens/latence/erreurs par fonctionnalité.
/// Activation/désactivation via /admin/settings (PUT /api/admin/config,
/// clés `ai.features.<nom>`) — JAMAIS de clé API côté frontend.
export default function AdminAi() {
  usePageMeta('IA', 'Monitoring de l’intelligence artificielle')
  const [stats, setStats] = useState<AiUsageStats | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setStatus('loading')
    try {
      setStats(await aiApi.usage())
      setError(null)
      setStatus('ready')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Chargement impossible')
      setStatus('error')
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-bold text-text-primary">Intelligence artificielle</h1>
          <p className="mt-1 text-sm text-text-secondary">
            {stats ? (
              <>
                Provider <Badge variant="neutral">{stats.provider}</Badge>{' '}
                Modèle <Badge variant="neutral">{stats.model}</Badge>{' '}
                {stats.enabled
                  ? <Badge variant="accent">Activé</Badge>
                  : <Badge variant="danger">Désactivé</Badge>}
              </>
            ) : 'Monitoring de l’assistant Flick (7 derniers jours).'}
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => void load()}>Actualiser</Button>
      </div>

      {status === 'loading' && <Loader />}
      {status === 'error' && (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      )}

      {status === 'ready' && stats && (
        <>
          <Card>
            <h2 className="text-sm font-semibold text-text-primary">Usage par fonctionnalité</h2>
            {stats.features.length === 0 ? (
              <p className="mt-1 text-sm text-text-secondary">Aucune requête IA sur la période.</p>
            ) : (
              <div className="mt-2 overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="text-xs text-text-secondary">
                      <th className="py-2 pr-4">Fonctionnalité</th>
                      <th className="py-2 pr-4">Requêtes</th>
                      <th className="py-2 pr-4">Tokens</th>
                      <th className="py-2 pr-4">Latence moy.</th>
                      <th className="py-2">Erreurs</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.features.map((feature) => (
                      <tr key={feature.feature} className="border-t border-border">
                        <td className="py-2 pr-4 font-medium text-text-primary">{feature.feature}</td>
                        <td className="py-2 pr-4 text-text-secondary">{feature.requests}</td>
                        <td className="py-2 pr-4 text-text-secondary">{feature.tokens}</td>
                        <td className="py-2 pr-4 text-text-secondary">{Math.round(feature.avgLatencyMs)} ms</td>
                        <td className="py-2">
                          {feature.errors > 0
                            ? <Badge variant="danger">{feature.errors}</Badge>
                            : <Badge variant="neutral">0</Badge>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <Card>
            <h2 className="text-sm font-semibold text-text-primary">Dernières erreurs</h2>
            {stats.recentErrors.length === 0 ? (
              <p className="mt-1 text-sm text-text-secondary">Aucune erreur récente.</p>
            ) : (
              <ul className="mt-2 space-y-1 text-sm text-text-secondary">
                {stats.recentErrors.map((entry) => (
                  <li key={entry.id}>
                    <Badge variant="danger">{entry.status}</Badge>{' '}
                    {entry.feature} — {entry.errorCode ?? 'sans code'}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          <Card>
            <p className="text-sm text-text-secondary">
              Activer/désactiver une fonctionnalité : <Link to="/admin/settings" className="text-primary-400 hover:underline">Configuration</Link>,
              clés <code>ai.features.&lt;nom&gt;</code> (ex. <code>ai.features.titles = false</code>).
              Les clés API (<code>AI_API_KEY</code>) vivent dans l'environnement serveur — jamais ici.
            </p>
          </Card>
        </>
      )}
    </div>
  )
}
