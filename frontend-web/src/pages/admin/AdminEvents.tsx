import { Badge, Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Événements plateforme (étape 17) : /admin/events.
/// Aucun module événementiel backend : base propre pour la suite, sans
/// événement fictif.
export default function AdminEvents() {
  usePageMeta('Événements', 'Événements de la plateforme')
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Événements</h1>
      <Card>
        <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
          Tournois, festivals, événements gaming et musicaux
          <Badge variant="neutral">Bientôt disponible</Badge>
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          Cette page accueillera : nom, description, dates de début et fin, statut, visuel —
          une fois le module événementiel défini côté backend.
        </p>
      </Card>
    </div>
  )
}
