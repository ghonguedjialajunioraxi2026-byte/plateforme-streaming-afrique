/// Chaînes suivies RÉELLES pour les sidebars (jamais de mocks).
/// Connecté : suivis serveur (`GET /follows/{id}/following`) croisés avec
/// les lives en cours (`GET /streams?status=LIVE`) pour le badge LIVE.
/// Visiteur : `undefined` (la section « Pour vous » est masquée).
import { useEffect, useState } from 'react'
import type { FollowedChannel } from '../../constants/discover'
import { followsApi } from '../../services/api/follows'
import { streamsApi } from '../../services/api/streams'
import { useAuth } from '../auth/useAuth'

export function useFollowedChannels(): FollowedChannel[] | undefined {
  const { user, isAuthenticated } = useAuth()
  const [followed, setFollowed] = useState<FollowedChannel[] | undefined>(undefined)

  useEffect(() => {
    if (!isAuthenticated || !user) {
      setFollowed(undefined)
      return
    }
    let cancelled = false
    async function load() {
      try {
        const [page, livePage] = await Promise.all([
          followsApi.followedChannels(user!.id, 0, 10),
          streamsApi.listLive(0, 50).catch(() => null),
        ])
        if (cancelled) return
        const liveByCreator = new Map<number, { viewers: number; category: string }>()
        for (const live of livePage?.content ?? []) {
          if (!liveByCreator.has(live.creatorId)) {
            liveByCreator.set(live.creatorId, {
              viewers: live.viewerCount,
              category: live.categoryName ?? '',
            })
          }
        }
        setFollowed(
          page.content.map((channel) => {
            const live = liveByCreator.get(channel.id)
            return {
              id: String(channel.id),
              slug: channel.slug,
              name: channel.displayName,
              category: channel.primaryCategory?.name ?? '',
              viewers: live?.viewers ?? 0,
              isLive: live !== undefined,
              followers: channel.followerCount,
            }
          }),
        )
      } catch {
        if (!cancelled) setFollowed([])
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [isAuthenticated, user])

  return followed
}
