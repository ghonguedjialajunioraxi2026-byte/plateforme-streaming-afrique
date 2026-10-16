import { Link } from 'react-router'
import { Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Support bêta (étape 25) : /support.
/// Canaux simples : aide par catégorie + formulaire de retour existant.
/// Aucune donnée sensible demandée au-delà du retour lui-même.
export default function Support() {
  usePageMeta('Support', 'Aide FLICK : compte, streaming, paiement, bug.')
  const topics = [
    { title: 'Compte et connexion', text: 'Inscription, mot de passe oublié, vérification email, suppression de compte (paramètres).' },
    { title: 'Streaming et créateurs', text: 'Devenir créateur, configurer OBS, lancer un live, modérer son chat.' },
    { title: 'Paiements (sandbox)', text: 'Aucune transaction réelle en bêta : soldes et cadeaux simulés, historique dans le portefeuille.' },
    { title: 'Bug ou idée', text: 'Décris le problème avec la page concernée : l\u2019équipe le trie et te répond via le statut de ton retour.' },
  ]
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Support</h1>
      {topics.map((topic) => (
        <Card key={topic.title}>
          <h2 className="font-bold">{topic.title}</h2>
          <p className="mt-1 text-sm text-text-secondary">{topic.text}</p>
        </Card>
      ))}
      <Card>
        <p className="text-sm text-text-secondary">
          Toujours bloqué ?{' '}
          <Link to="/feedback" className="text-primary-400 hover:underline">Signale un retour</Link>
          {' '}ou consulte l&apos;état des services sur la{' '}
          <Link to="/statut" className="text-primary-400 hover:underline">page de statut</Link>.
        </p>
      </Card>
    </div>
  )
}
