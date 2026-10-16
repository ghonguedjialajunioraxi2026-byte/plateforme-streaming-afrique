import { useEffect, useState } from 'react'
import { Card, EmptyState, Loader } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'
import { http } from '../../services/http'

interface Health {
  status?: string
}

interface Info {
  app?: { name?: string; version?: string }
  build?: { version?: string; time?: string }
}

/// Page de statut publique (étape 25) : /statut.
/// Données RÉELLES uniquement (/api/health + /api/info) — jamais de
/// façade « tout est vert » : l'indisponible s'affiche comme tel.
export default function Status() {
  usePageMeta('Statut', 'Disponibilité réelle des services FLICK.')
  const [health, setHealth] = useState<Health | null>(null)
  const [info, setInfo] = useState<Info | null>(null)
  const [loading, setLoading] = useState(true)
  const [reachable, setReachable] = useState(true)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const [h, i] = await Promise.all([
          http.get<Health>('/health'),
          http.get<Info>('/info'),
        ])
        if (!cancelled) {
          setHealth(h)
          setInfo(i)
          setReachable(true)
        }
      } catch {
        if (!cancelled) setReachable(false)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [])

  const up = reachable && health?.status === 'UP'
  const version = info?.build?.version ?? info?.app?.version ?? '—'

  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Statut des services</h1>
      {loading ? (
        <Loader />
      ) : !reachable ? (
        <EmptyState title="API injoignable" description="Le backend ne répond pas pour le moment." />
      ) : (
        <>
          <Card>
            <div className="flex items-center gap-2">
              <span aria-hidden className={`inline-block h-3 w-3 rounded-full ${up ? 'bg-success' : 'bg-danger'}`} />
              <strong>{up ? 'OPÉRATIONNEL' : 'INCIDENT'}</strong>
            </div>
            <p className="mt-1 text-sm text-text-secondary">API : {health?.status ?? 'inconnu'} · version {version}</p>
          </Card>
          <Card>
            <p className="text-sm text-text-secondary">
              Détail par service (streaming, chat, paiements) : monitoring interne.
              Un incident ? <a href="/feedback" className="text-primary-400 hover:underline">Signale-le</a>.
            </p>
          </Card>
        </>
      )}
    </div>
  )
}
