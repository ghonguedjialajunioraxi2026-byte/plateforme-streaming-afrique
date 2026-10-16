import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from '../features/auth/useAuth'
import { Loader } from '../components/ui'

export type UserRole = 'USER' | 'CREATOR' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN'

interface ProtectedRouteProps {
  children: ReactNode
  /** Rôles autorisés. Vide = n'importe quel utilisateur connecté. */
  allowedRoles?: UserRole[]
}

/**
 * Protège une route selon l'authentification RÉELLE (étape 08).
 *
 * - chargement (`/auth/me` en cours) → spinner plein écran (pas de flash) ;
 * - anonyme → `/connexion` (avec `state.from` pour retour après login) ;
 * - rôle interdit → `/` (la protection VRAIE reste le backend : les
 *   `@PreAuthorize` refusent en 403, ce guard n'est que de l'UX).
 */
export default function ProtectedRoute({
  children,
  allowedRoles = [],
}: ProtectedRouteProps) {
  const location = useLocation()
  const { user, isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loader />
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/connexion" replace state={{ from: location }} />
  }

  if (allowedRoles.length > 0 && !allowedRoles.includes(user?.role ?? 'USER')) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
