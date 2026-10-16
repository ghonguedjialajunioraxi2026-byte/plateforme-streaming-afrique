import { useContext } from 'react'
import { AuthContext } from './authContextValue'

/** Accès au store auth — hors provider = erreur de programmation (fail-fast). */
export function useAuth() {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth doit être utilisé dans <AuthProvider>')
  }
  return context
}
