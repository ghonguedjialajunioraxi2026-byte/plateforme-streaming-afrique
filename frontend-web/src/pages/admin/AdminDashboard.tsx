import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Card, Loader, useToast } from '../../components/ui'
import { StatCard } from '../../components/creator/StatCard'
import { formatCount } from '../../utils/format'
import { adminApi, type AdminDashboard } from '../../services/api/admin'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Tableau de bord administration (étape 17) : /admin.
/// Compteurs plateforme 100 % backend (aucune statistique inventée).
export default function AdminDashboard() {
  usePageMeta('Administration', 'Pilotage de la plateforme')
  const toast = useToast()
  const [data, setData] = useState<AdminDashboard | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const result = await adminApi.dashboard()
        if (!cancelled) {
          setData(result)
          setStatus('ready')
        }
      } catch (loadError) {
        if (cancelled) return
        setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
        setStatus('error')
        toast.show('Tableau de bord inaccessible', 'error')
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [toast])

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Administration</h1>

      {status === 'loading' && (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
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
          <section aria-label="Comptes et chaînes">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="Utilisateurs" value={formatCount(data.usersTotal)} hint={`${formatCount(data.usersActive)} actifs`} />
              <StatCard label="Suspendus" value={formatCount(data.usersSuspended)} hint="comptes SUSPENDED" />
              <StatCard label="Chaînes" value={formatCount(data.creatorsTotal)} hint="créateurs" />
              <StatCard label="Lives en cours" value={formatCount(data.liveStreams)} hint="temps réel" />
            </div>
          </section>

          <section aria-label="Contenus">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="VOD" value={formatCount(data.videosTotal)} />
              <StatCard label="Clips" value={formatCount(data.clipsTotal)} />
              <StatCard label="Followers" value={formatCount(data.followersTotal)} hint="liens de suivi" />
              <StatCard label="Abonnements" value={formatCount(data.subscriptionsActive)} hint="payants actifs" />
            </div>
          </section>

          <div className="grid gap-4 lg:grid-cols-2">
            <section aria-label="Paiements par statut">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Paiements par statut</h2>
              <Card>
                {Object.keys(data.paymentsByStatus).length === 0 ? (
                  <p className="text-sm text-text-secondary">Aucun paiement enregistré.</p>
                ) : (
                  <dl className="space-y-1.5">
                    {Object.entries(data.paymentsByStatus).map(([paymentStatus, count]) => (
                      <div key={paymentStatus} className="flex items-center justify-between gap-4 text-sm">
                        <dt className="font-mono text-xs text-text-secondary">{paymentStatus}</dt>
                        <dd className="font-semibold text-text-primary">{formatCount(count)}</dd>
                      </div>
                    ))}
                  </dl>
                )}
                <p className="mt-2 text-xs text-text-muted">
                  Montants multi-devises : aucun total monétaire global (voir /admin/payments).
                </p>
              </Card>
            </section>

            <section aria-label="Vigilance">
              <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Vigilance</h2>
              <div className="grid grid-cols-2 gap-3">
                <Link to="/admin/reports">
                  <Card className="transition-colors hover:border-warning/50">
                    <p className="text-xs uppercase text-text-muted">Signalements ouverts</p>
                    <p className="mt-1 font-display text-2xl font-bold text-text-primary">{formatCount(data.openReports)}</p>
                  </Card>
                </Link>
                <Link to="/admin/logs">
                  <Card className="transition-colors hover:border-primary-400/50">
                    <p className="text-xs uppercase text-text-muted">Actes d'audit</p>
                    <p className="mt-1 font-display text-2xl font-bold text-text-primary">{formatCount(data.auditEntries)}</p>
                  </Card>
                </Link>
              </div>
            </section>
          </div>
        </>
      )}
    </div>
  )
}
