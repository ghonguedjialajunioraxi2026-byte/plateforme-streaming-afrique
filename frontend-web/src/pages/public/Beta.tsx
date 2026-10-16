import { Link } from 'react-router'
import { Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'
import { BETA_APP_VERSION } from '../../services/api/beta'

/// Programme de bêta privée (étape 24) : /beta.
/// Page publique d'information — l'accès au produit reste gardé par
/// invitation côté backend (mode invite-only de l'environnement bêta).
export default function Beta() {
  usePageMeta('Bêta privée', 'Programme de test FLICK : conditions et signalement')
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">
        Bêta privée ({BETA_APP_VERSION})
      </h1>
      <p className="text-sm text-text-secondary">
        FLICK est en bêta privée : accès sur invitation uniquement, fonctionnalités
        expérimentales, bugs possibles. En participant, tu acceptes que ton usage
        (pages visitées, erreurs techniques) aide à stabiliser la plateforme —
        aucun mot de passe, message privé ou contenu sensible n&apos;est exploité.
      </p>
      <Card>
        <h2 className="font-bold">Comment participer</h2>
        <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-text-secondary">
          <li>Reçois un code d&apos;invitation d&apos;un administrateur.</li>
          <li>Crée ton compte avec ce code (champ « Code d&apos;invitation »).</li>
          <li>Complète ton profil, explore, regarde un live, discute.</li>
          <li>Signale tout problème via « Retour bêta » (menu utilisateur).</li>
        </ol>
        <div className="mt-3 flex flex-wrap gap-2">
          <Link to="/inscription" className="text-sm text-primary-400 hover:underline">
            Créer un compte
          </Link>
          <Link to="/feedback" className="text-sm text-primary-400 hover:underline">
            Signaler un retour
          </Link>
        </div>
      </Card>
      <Card>
        <h2 className="font-bold">Ce qui est testé</h2>
        <p className="mt-2 text-sm text-text-secondary">
          Lives, chat, follows, VOD, clips, wallet et paiements en sandbox
          (aucune transaction réelle), application mobile, mode économie de
          données. Tes retours sont triés par l&apos;équipe (statuts visibles
          dans « Retour bêta »).
        </p>
      </Card>
    </div>
  )
}
