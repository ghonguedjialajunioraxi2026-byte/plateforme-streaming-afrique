import { useEffect, useState } from 'react'
import { useAuth } from '../features/auth/useAuth'
import { notificationsApi } from '../services/api/notifications'

/**
 * Compteur de notifications non lues partagé (cloche mobile + desktop).
 * Rafraîchit toutes les 60s quand l'utilisateur est connecté ; silencieux
 * en cas d'erreur (la page Notifications reste la référence).
 */
export function useUnreadCount(externalCount = 0): number {
  const { user, isAuthenticated } = useAuth()
  const [count, setCount] = useState(externalCount)

  useEffect(() => {
    if (!isAuthenticated || user === null) {
      setCount(externalCount)
      return
    }
    let cancelled = false
    const userId = user.id
    async function refresh() {
      try {
        const value = await notificationsApi.unreadCount(userId)
        if (!cancelled) setCount(value)
      } catch {
        // Cloche silencieuse.
      }
    }
    void refresh()
    const timer = window.setInterval(() => void refresh(), 60000)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [isAuthenticated, user, externalCount])

  return count
}
