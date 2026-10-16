import { Link } from 'react-router'
import { Card } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Règles communautaires (étape 25) : /regles.
/// Mêmes catégories que le signalement (modération) pour rester cohérent.
export default function CommunityRules() {
  usePageMeta('Règles communautaires', 'Ce qui est attendu et interdit sur FLICK.')
  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Règles communautaires</h1>
      <Card>
        <ul className="list-disc space-y-2 pl-5 text-sm text-text-secondary">
          <li><strong>Respect :</strong> aucun harcèlement, discours haineux ou menace.</li>
          <li><strong>Sécurité :</strong> aucun contenu violent, sexuel impliquant des mineurs, ni apologie.</li>
          <li><strong>Honnêteté :</strong> ni spam, ni fraude, ni usurpation d&apos;identité, ni faux engagement.</li>
          <li><strong>Droits :</strong> ne diffuse que des contenus dont tu as les droits.</li>
          <li><strong>Chat :</strong> pas de flood, de liens malveillants ni de contournement de sanction.</li>
        </ul>
      </Card>
      <Card>
        <h2 className="font-bold">Signaler et sanctions</h2>
        <p className="mt-2 text-sm text-text-secondary">
          Signale depuis un contenu ou via « Retour bêta ». Sanctions graduées : avertissement,
          timeout, ban, suspension de compte — tracées et réversibles quand prévu.{' '}
          <Link to="/feedback" className="text-primary-400 hover:underline">Signaler un problème</Link>
        </p>
      </Card>
    </div>
  )
}
