import { Link } from 'react-router'
import { EmptyState } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'

/**
 * Messages privés : la messagerie temps réel n'est pas encore fournie
 * par le backend — état vide honnête (aucune conversation simulée),
 * avec un point d'entrée vers la communauté.
 */
export default function Messages() {
  usePageMeta('Messages', 'Tes conversations privées sur FLICK.')

  return (
    <div className="mx-auto w-full max-w-3xl">
      <h1 className="font-display mb-6 text-[22px] font-bold tracking-tight text-text-primary md:text-2xl">
        Messages
      </h1>
      <EmptyState
        icon="chat"
        title="Aucune conversation"
        description="Tes messages privés avec les créateurs et ta communauté apparaîtront ici dès que la messagerie sera disponible."
        action={
          <Link
            to="/explorer"
            className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
          >
            Découvrir des créateurs
          </Link>
        }
      />
    </div>
  )
}
