import { Badge, Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Publicité (étape 17) : /admin/advertising.
/// Aucun système publicitaire backend (ni entité, ni endpoint) : base propre
/// pour la suite, sans campagne fictive.
export default function AdminAdvertising() {
  usePageMeta('Publicité', 'Administration publicitaire')
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Publicité</h1>
      <Card>
        <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
          Campagnes et annonceurs
          <Badge variant="neutral">Bientôt disponible</Badge>
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          Cette page accueillera : campagnes, bannières, annonceurs, statut, dates,
          impressions, clics — une fois le module publicitaire défini côté backend.
        </p>
      </Card>
    </div>
  )
}
