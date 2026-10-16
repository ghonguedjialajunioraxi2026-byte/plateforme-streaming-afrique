import { Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Politique de confidentialité (étape 25) : /confidentialite.
/// Données minimales, finalités explicites, droits d'accès et suppression.
export default function Privacy() {
  usePageMeta('Confidentialité', 'Données collectées par FLICK, finalités et droits.')
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Politique de confidentialité</h1>
      <Card>
        <h2 className="font-bold">Données collectées</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-text-secondary">
          <li>Compte : email, pseudonyme, mot de passe chiffré (jamais en clair, jamais journalisé).</li>
          <li>Profil : avatar, bio, préférences de langue et région si tu les renseignes.</li>
          <li>Usage : pages visitées, lives regardés, erreurs techniques (diagnostic et stabilité).</li>
        </ul>
      </Card>
      <Card>
        <h2 className="font-bold">Ce que nous ne collectons pas</h2>
        <p className="mt-2 text-sm text-text-secondary">
          Ni localisation précise, ni données bancaires (sandbox en bêta), ni contenu de tes messages
          privés à des fins publicitaires. Les retours bêta n&apos;incluent jamais mot de passe ni jeton.
        </p>
      </Card>
      <Card>
        <h2 className="font-bold">Tes droits</h2>
        <p className="mt-2 text-sm text-text-secondary">
          Accès, correction et suppression de ton compte depuis les paramètres. Les emails techniques
          (vérification, récupération) ne servent qu&apos;à la sécurité du compte.
        </p>
      </Card>
    </div>
  )
}
