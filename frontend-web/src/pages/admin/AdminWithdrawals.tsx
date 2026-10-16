import { Badge, Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Retraits (étape 17) : /admin/withdrawals.
/// AUCUN système de retrait réel n'existe côté backend (ni entité, ni
/// endpoint) : pas de fausse file d'approbation. Structure d'accueil pour
/// la future administration des demandes (montant, devise, statut, méthode).
export default function AdminWithdrawals() {
  usePageMeta('Retraits', 'Administration des demandes de retrait')
  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Retraits</h1>
      <Card>
        <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-text-primary">
          Demandes de retrait
          <Badge variant="neutral">Bientôt disponible</Badge>
        </p>
        <p className="mt-1 text-xs text-text-secondary">
          Le circuit de retrait (demande → approbation → paiement → suivi) n’est pas encore
          implémenté côté backend. Cette page accueillera : créateur, montant, devise, statut
          (PENDING, PROCESSING, APPROVED, REJECTED, PAID, FAILED), méthode, référence, date.
        </p>
      </Card>
      <Card>
        <p className="text-sm text-text-secondary">
          En attendant : les mouvements de coins restent consultables par chaîne sur
          /creator/revenue et les paiements sur /admin/payments (lecture seule).
        </p>
      </Card>
    </div>
  )
}
