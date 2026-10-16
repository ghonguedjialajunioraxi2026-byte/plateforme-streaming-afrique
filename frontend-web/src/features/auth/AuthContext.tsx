/// État global d'authentification (étape 08) — Context + Reducer, zéro
/// dépendance (pas de Zustand : un seul store, un seul producteur).
///
/// ```text
/// user / isAuthenticated / isLoading
/// login() / register() / logout() / loadCurrentUser()
/// ```
/// (le refresh est interne à l'intercepteur `http`, single-flight).
///
/// Cycle de vie : au montage, si un refresh token persiste → `me()` direct
/// (access en mémoire perdu au rechargement, refresh en localStorage) ;
/// échec → tentative `refresh()` silencieuse ; échec → anonyme.
/// Expiration de session (intercepteur `http`) → logout + `/connexion`.
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { authApi, type AuthUser } from '../../services/api/auth'
import {
  getRefreshToken,
  setAccessToken,
  setAuthExpiredHandler,
  setRefreshToken,
  ApiError,
} from '../../services/http'
import { AuthContext, type AuthContextValue } from './authContextValue'

function toMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.message
  }
  return fallback
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const logoutLocal = useCallback(() => {
    setAccessToken(null)
    setRefreshToken(null)
    setUser(null)
  }, [])

  // Pas de navigation ici (le provider vit AU-DESSUS du RouterProvider) :
  // l'appelant redirige après logout(), et `ProtectedRoute` redirige
  // automatiquement quand `user` repasse à null (expiration).
  const logout = useCallback(async () => {
    const refreshToken = getRefreshToken()
    logoutLocal()
    setError(null)
    if (refreshToken !== null) {
      try {
        await authApi.logout(refreshToken)
      } catch {
        // Révocation best-effort : le local prime, le serveur a sa TTL.
      }
    }
  }, [logoutLocal])

  // Expiration détectée par l'intercepteur → état anonyme (les guards suivent).
  useEffect(() => {
    setAuthExpiredHandler(() => {
      logoutLocal()
    })
    return () => {
      setAuthExpiredHandler(null)
    }
  }, [logoutLocal])

  const loadCurrentUser = useCallback(async () => {
    const me = await authApi.me()
    setUser(me)
  }, [])

  // Restauration au chargement : access perdu (mémoire), refresh persisté.
  useEffect(() => {
    let cancelled = false
    async function restore() {
      try {
        await loadCurrentUser()
      } catch {
        const refreshToken = getRefreshToken()
        if (refreshToken !== null) {
          try {
            const pair = await authApi.refresh(refreshToken)
            setAccessToken(pair.accessToken)
            setRefreshToken(pair.refreshToken)
            if (!cancelled) {
              await loadCurrentUser()
            }
          } catch {
            if (!cancelled) {
              logoutLocal()
            }
          }
        } else if (!cancelled) {
          logoutLocal()
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [loadCurrentUser, logoutLocal])

  const login = useCallback(
    async (email: string, password: string) => {
      setError(null)
      try {
        const response = await authApi.login({ email, password })
        setAccessToken(response.accessToken)
        setRefreshToken(response.refreshToken)
        await loadCurrentUser()
      } catch (error) {
        throw new Error(toMessage(error, 'Connexion impossible'))
      }
    },
    [loadCurrentUser],
  )

  const loginWithGoogle = useCallback(
    async (idToken: string) => {
      setError(null)
      try {
        const response = await authApi.google(idToken)
        setAccessToken(response.accessToken)
        setRefreshToken(response.refreshToken)
        await loadCurrentUser()
      } catch (error) {
        throw new Error(toMessage(error, 'Connexion Google impossible'))
      }
    },
    [loadCurrentUser],
  )

  const linkGoogle = useCallback(async (idToken: string) => {
    setError(null)
    try {
      const me = await authApi.linkGoogle(idToken)
      setUser(me)
    } catch (error) {
      throw new Error(toMessage(error, 'Liaison Google impossible'))
    }
  }, [])

  const register = useCallback(async (input: { username: string; email: string; password: string; inviteCode?: string }) => {
    setError(null)
    try {
      await authApi.register({ ...input, confirmPassword: input.password })
    } catch (error) {
      if (error instanceof ApiError && error.errors !== undefined) {
        const first = Object.values(error.errors)[0]
        throw new Error(first ?? error.message)
      }
      throw new Error(toMessage(error, 'Inscription impossible'))
    }
  }, [])

  const clearError = useCallback(() => {
    setError(null)
  }, [])

  const refreshSession = useCallback(async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken === null) {
      throw new Error('Aucune session à renouveler')
    }
    try {
      const pair = await authApi.refresh(refreshToken)
      setAccessToken(pair.accessToken)
      setRefreshToken(pair.refreshToken)
      await loadCurrentUser()
    } catch (error) {
      throw new Error(toMessage(error, 'Renouvellement de session impossible'))
    }
  }, [loadCurrentUser])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isLoading,
      error,
      login,
      loginWithGoogle,
      linkGoogle,
      register,
      logout,
      loadCurrentUser,
      refreshSession,
      clearError,
    }),
    [user, isLoading, error, login, loginWithGoogle, linkGoogle, register, logout, loadCurrentUser, refreshSession, clearError],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
