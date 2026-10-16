import { Card } from '../ui'

/// Carte statistique du Creator Studio (étape 16) : libellé + valeur.
/// Valeurs toujours issues du backend (`creatorDashboardApi`) — jamais
/// de chiffres fictifs ; l'absence de données se traduit par 0 ou un
/// état vide explicite dans la page appelante.
export function StatCard({
  label,
  value,
  hint,
}: {
  label: string
  value: string
  hint?: string
}) {
  return (
    <Card className="min-w-0">
      <p className="truncate text-xs font-medium uppercase tracking-wide text-text-muted">
        {label}
      </p>
      <p className="mt-1 truncate font-display text-2xl font-bold text-text-primary">
        {value}
      </p>
      {hint !== undefined && (
        <p className="mt-0.5 truncate text-xs text-text-secondary">{hint}</p>
      )}
    </Card>
  )
}
