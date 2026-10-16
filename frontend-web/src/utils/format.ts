/** Formatte un nombre en notation compacte : 12500 → "12,5k", 1_200_000 → "1,2M" */
export function formatCount(value: number): string {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1).replace('.', ',').replace(',0', '')}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1).replace('.', ',').replace(',0', '')}k`
  }
  return String(value)
}

/** Formatte une durée en secondes : 25 → "0:25", 125 → "2:05", 3725 → "1:02:05" */
export function formatDuration(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(clamped / 3600)
  const minutes = Math.floor((clamped % 3600) / 60)
  const seconds = clamped % 60
  const tail = `${String(minutes).padStart(hours > 0 ? 2 : 1, '0')}:${String(seconds).padStart(2, '0')}`
  return hours > 0 ? `${hours}:${tail}` : tail
}

/** Formatte un ISO en date FR courte : "14/09/2026" */
export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' })
}