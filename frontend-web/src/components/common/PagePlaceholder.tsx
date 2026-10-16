import { useLocation } from 'react-router'

interface PagePlaceholderProps {
  title: string
  description: string
  section?: string
}

export default function PagePlaceholder({
  title,
  description,
  section,
}: PagePlaceholderProps) {
  const location = useLocation()

  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      {section && (
        <span className="mb-3 rounded-full bg-primary-500/10 px-3 py-1 text-xs font-semibold text-primary-400">
          {section}
        </span>
      )}
      <h1 className="font-display text-3xl font-bold text-text-primary">
        {title}
      </h1>
      <p className="mt-2 max-w-md text-text-secondary">{description}</p>
      <div className="mt-6 rounded-lg bg-bg-surface px-4 py-2 font-mono text-sm text-text-muted">
        {location.pathname}
      </div>
      <p className="mt-4 text-xs text-text-muted">
        Cette page sera développée prochainement.
      </p>
    </div>
  )
}