import { Button } from '../ui'

/// Pagination partagée des studios (étape 17) : Précédent / Suivant.
/// `page` est 0-indexée (convention backend) ; affichage 1-indexé.
export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage,
}: {
  page: number
  totalPages: number
  totalElements: number
  onPage: (page: number) => void
}) {
  if (totalPages <= 1 && totalElements === 0) return null
  return (
    <nav className="flex flex-wrap items-center justify-between gap-2" aria-label="Pagination">
      <p className="text-xs text-text-muted" aria-live="polite">
        Page {page + 1} sur {Math.max(1, totalPages)} · {totalElements} élément(s)
      </p>
      <div className="flex gap-1.5">
        <Button
          variant="ghost"
          size="sm"
          disabled={page <= 0}
          onClick={() => onPage(page - 1)}
        >
          Précédent
        </Button>
        <Button
          variant="ghost"
          size="sm"
          disabled={page + 1 >= totalPages}
          onClick={() => onPage(page + 1)}
        >
          Suivant
        </Button>
      </div>
    </nav>
  )
}
