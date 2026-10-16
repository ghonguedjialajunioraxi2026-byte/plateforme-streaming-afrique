import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { CreatorCard, CreatorCardSkeleton } from '../../components/streaming'
import { SectionHeader } from '../../components/common/SectionHeader'
import { EmptyState } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'
import { useAuth } from '../../features/auth/useAuth'
import { followsApi } from '../../services/api/follows'
import type { ChannelDetail } from '../../types/domain'

/**
 * Chaînes suivies façon Twitch : grille de cartes créateurs
 * (données serveur), skeletons au chargement, état vide avec
 * sortie vers l'explorer. Le désuivi se fait depuis la page
 * chaîne / live (bouton Suivre contextuel).
 */
export default function Following() {
  usePageMeta('Chaînes suivies', 'Retrouve les créateurs que tu suis et leurs derniers lives.')
  const { user } = useAuth()
  const [channels, setChannels] = useState<ChannelDetail[]>([])
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    if (user === null) return
    setLoading(true)
    try {
      const page = await followsApi.followedChannels(user.id, 0, 30)
      setChannels(page.content)
    } catch {
      setChannels([])
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <div className="mx-auto w-full max-w-[1600px] space-y-6">
      <SectionHeader
        title="Chaînes suivies"
        subtitle={
          loading
            ? 'Chargement…'
            : `${channels.length} chaîne${channels.length > 1 ? 's' : ''}`
        }
      />

      {loading && (
        <div
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-4"
          aria-busy="true"
          aria-label="Chargement des chaînes suivies"
        >
          {Array.from({ length: 8 }).map((_, index) => (
            <CreatorCardSkeleton key={index} />
          ))}
        </div>
      )}

      {!loading && channels.length === 0 && (
        <EmptyState
          icon="heart"
          title="Tu ne suis aucune chaîne"
          description="Suis tes créateurs préférés pour retrouver leurs lives ici et être alerté de leurs passages en direct."
          action={
            <Link
              to="/explorer"
              className="mt-1 rounded-lg bg-primary-500 px-4 py-2 text-sm font-semibold text-text-inverse transition-colors duration-200 hover:bg-primary-400"
            >
              Explorer les lives
            </Link>
          }
        />
      )}

      {!loading && channels.length > 0 && (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-4">
          {channels.map((channel) => (
            <CreatorCard
              key={channel.id}
              channel={{
                id: String(channel.id),
                userId: String(channel.userId),
                name: channel.displayName,
                slug: channel.slug,
                description: channel.bio ?? undefined,
                followersCount: channel.followerCount,
                isLive: false,
              }}
            />
          ))}
        </div>
      )}
    </div>
  )
}
