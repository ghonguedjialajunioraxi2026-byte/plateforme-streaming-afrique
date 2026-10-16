import { Link } from 'react-router'
import { FlickLogo } from '../components/branding'

export default function NotFound() {
  return (
    <div className="flex min-h-[80vh] flex-col items-center justify-center text-center">
      <FlickLogo variant="mark" size="lg" link={false} />
      <p className="mt-6 font-mono text-8xl font-bold text-primary-500">404</p>
      <h1 className="mt-4 font-display text-3xl font-bold text-text-primary">
        Page introuvable
      </h1>
      <p className="mt-2 max-w-md text-text-secondary">
        La page que vous recherchez n'existe pas ou a été déplacée.
        Vérifiez l'adresse ou revenez à l'accueil.
      </p>
      <div className="mt-8 flex gap-3">
        <Link
          to="/"
          className="rounded-lg bg-primary-500 px-6 py-3 font-semibold text-text-inverse transition-all hover:bg-primary-600 hover:shadow-glow-primary"
        >
          Retour à l'accueil
        </Link>
        <Link
          to="/explorer"
          className="rounded-lg border border-border-default bg-bg-surface px-6 py-3 font-semibold text-text-secondary transition-all hover:border-border-hover hover:text-text-primary"
        >
          Explorer
        </Link>
      </div>
    </div>
  )
}