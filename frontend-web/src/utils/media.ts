/// URLs d'images (étape 09) — le backend renvoie des chemins RELATIFS
/// (`/uploads/avatars/uuid.png`) servis par Spring Boot (:8080), pas par
/// Vite (:5173). Ce helper préfixe l'origine de l'API ; les URLs absolues
/// (CDN) passent telles quelles ; vide → `null` (fallback Avatar/initiales).

const API_ORIGIN = (() => {
  const base = import.meta.env.VITE_API_URL as string | undefined
  const root = base ?? 'http://localhost:8080/api'
  return root.endsWith('/api') ? root.slice(0, -'/api'.length) : root
})()

export function mediaUrl(url: string | null | undefined): string | null {
  if (url === null || url === undefined) {
    return null
  }
  const trimmed = url.trim()
  if (trimmed.length === 0) {
    return null
  }
  if (trimmed.startsWith('/uploads/')) {
    return `${API_ORIGIN}${trimmed}`
  }
  return trimmed
}
