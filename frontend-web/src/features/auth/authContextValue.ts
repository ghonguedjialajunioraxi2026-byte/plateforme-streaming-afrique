import { createContext } from 'react'
import type { AuthUser } from '../../services/api/auth'

export interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  error: string | null
  login: (email: string, password: string) => Promise<void>
  /**
   * Connexion Google : id_token vérifié côté serveur → paire JWT FLICK
   * (même session que le login classique). Lève 409 si l'email appartient
   * à un compte LOCAL (liaison via `linkGoogle` après login mot de passe).
   */
  loginWithGoogle: (idToken: string) => Promise<void>
  /** Lie le compte Google au compte connecté (CAS 3 résolu par l'utilisateur). */
  linkGoogle: (idToken: string) => Promise<void>
  register: (input: { username: string; email: string; password: string; inviteCode?: string }) => Promise<void>
  logout: () => Promise<void>
  loadCurrentUser: () => Promise<void>
  /**
   * Renouvelle la session (rotation refresh → nouveaux jetons).
   * À appeler après un changement de rôle côté serveur (ex. ouverture de
   * chaîne → CREATOR) : le JWT en mémoire porte sinon l'ancien rôle et les
   * gardes `@PreAuthorize` répondent 403 jusqu'à la prochaine connexion.
   */
  refreshSession: () => Promise<void>
  clearError: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
