import { Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Conditions d'utilisation (étape 25) : /conditions.
/// Version bêta : règles essentielles, lisibles, sans jargon.
export default function Terms() {
  usePageMeta('Conditions d\u2019utilisation', 'Règles d\u2019utilisation de la plateforme FLICK en bêta.')
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Conditions d&apos;utilisation</h1>
      <p className="text-sm text-text-secondary">Version bêta — dernières règles applicables affichées ici.</p>
      <Card>
        <ol className="list-decimal space-y-2 pl-5 text-sm text-text-secondary">
          <li>FLICK est en bêta : le service peut être interrompu, modifié ou réinitialisé pendant les tests.</li>
          <li>Un compte par personne ; tu es responsable de tes identifiants et de ton mot de passe.</li>
          <li>Contenus interdits : spam, harcèlement, haine, violence, contenu sexuel non consenti, fraude, usurpation.</li>
          <li>Les paiements en bêta sont simulés (sandbox) : aucune transaction réelle, aucune valeur monétaire créée.</li>
          <li>En cas d&apos;abus, FLICK peut avertir, suspendre ou fermer un compte (actions tracées et réversibles quand prévu).</li>
          <li>Tu peux supprimer ton compte depuis les paramètres ; certaines données techniques et financières sont conservées le temps nécessaire (sécurité, audit).</li>
        </ol>
      </Card>
    </div>
  )
}
