import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { cn } from '../../utils/cn'
import { Icon } from '../ui/icons'
import { useAuth } from '../../features/auth/useAuth'
import { useToast } from '../ui'
import { followsApi } from '../../services/api/follows'

interface FollowButtonProps {
  /** Id numérique de la chaîne (`CreatorProfile.id`). `null` = suivi local. */
  channelId: number | null
  channelName: string
  className?: string
}

const LOCAL_KEY = 'flick-local-follows'

function readLocalFollows(): string[] {
  try {
    const raw = window.localStorage.getItem(LOCAL_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((v): v is string => typeof v === 'string') : []
  } catch {
    return []
  }
}

/**
 * Bouton Suivre façon Twitch, tous états :
 * défaut → hover → chargement → Suivi (hover = « Ne plus suivre »).
 * Non connecté → redirection vers /connexion (retour après login).
 * Sans id serveur (données vitrine) → suivi local persistant.
 */
export function FollowButton({ channelId, channelName, className }: FollowButtonProps) {
  const { user, isAuthenticated } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const [following, setFollowing] = useState(false)
  const [checking, setChecking] = useState(isAuthenticated && channelId !== null)
  const [pending, setPending] = useState(false)

  // État initial : serveur si possible, local sinon.
  useEffect(() => {
    if (!isAuthenticated || channelId === null || user === null) {
      setFollowing(readLocalFollows().includes(channelName))
      setChecking(false)
      return
    }
    let cancelled = false
    setChecking(true)
    followsApi
      .isFollowing(user.id, channelId)
      .then((value) => {
        if (!cancelled) setFollowing(value)
      })
      .catch(() => {
        if (!cancelled) setFollowing(readLocalFollows().includes(channelName))
      })
      .finally(() => {
        if (!cancelled) setChecking(false)
      })
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, channelId, channelName, user])

  function toggleLocal(next: boolean) {
    try {
      const current = readLocalFollows()
      const updated = next
        ? [...new Set([...current, channelName])]
        : current.filter((name) => name !== channelName)
      window.localStorage.setItem(LOCAL_KEY, JSON.stringify(updated))
    } catch {
      // Persistance indisponible : l'état visuel reste correct.
    }
  }

  async function handleClick() {
    if (!isAuthenticated) {
      navigate(`/connexion?next=${encodeURIComponent(window.location.pathname)}`)
      return
    }
    if (pending || checking) return
    setPending(true)
    try {
      if (channelId !== null && user !== null) {
        if (following) {
          await followsApi.unfollow(user.id, channelId)
          toast.show(`Tu ne suis plus ${channelName}`, 'info')
        } else {
          await followsApi.follow(user.id, channelId)
          toast.show(`Tu suis désormais ${channelName} !`, 'success')
        }
        setFollowing(!following)
      } else {
        toggleLocal(!following)
        setFollowing(!following)
        toast.show(
          following ? `Tu ne suis plus ${channelName}` : `Tu suis désormais ${channelName} !`,
          following ? 'info' : 'success',
        )
      }
    } catch {
      toast.show('Action impossible pour le moment, réessaie.', 'error')
    } finally {
      setPending(false)
    }
  }

  const busy = checking || pending

  return (
    <button
      type="button"
      onClick={() => void handleClick()}
      disabled={busy}
      aria-pressed={following}
      aria-label={following ? `Ne plus suivre ${channelName}` : `Suivre ${channelName}`}
      className={cn(
        'group/follow flex h-9 items-center gap-1.5 rounded-lg px-4 text-sm font-semibold transition-all duration-200 disabled:cursor-wait disabled:opacity-60',
        following
          ? 'border border-border-default bg-bg-elevated text-text-primary hover:border-danger/60 hover:bg-danger/10 hover:text-danger'
          : 'bg-primary-500 text-text-inverse hover:bg-primary-400 active:scale-95',
        className,
      )}
    >
      {busy ? (
        <span aria-hidden="true" className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
      ) : (
        <Icon name="heart" className="h-4 w-4" />
      )}
      {busy ? '…' : following ? (
        <>
          <span className="group-hover/follow:hidden">Suivi ✓</span>
          <span className="hidden group-hover/follow:inline">Ne plus suivre</span>
        </>
      ) : (
        'Suivre'
      )}
    </button>
  )
}
