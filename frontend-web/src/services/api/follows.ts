/// Service suivis (étape 12) — miroir RÉEL de `FollowController` backend.
///
/// Règles serveur (ne jamais les dupliquer côté client, juste les refléter) :
/// - suivre/ne-plus-suivre : connecté, pour SOI-MÊME (`followerId` = mon id) ;
/// - `is-following`, compteurs : PUBLICS ;
/// - auto-suivi → 400, agir pour autrui → 403.
import { http } from '../http'
import type { ChannelPage } from './creators'

export interface FollowDto {
  id: number
  followerId: number
  followedId: number
  createdAt: string
}

export const followsApi = {
  /** Suivre une chaîne (idempotent — 201 créé ou suivi existant). */
  follow: (followerId: number, channelId: number) =>
    http.post<FollowDto>('/follows', { followerId, channelId }),

  /** Ne plus suivre (404 si le suivi n'existe pas). */
  unfollow: (followerId: number, channelId: number) =>
    http.delete<null>(`/follows?followerId=${followerId}&channelId=${channelId}`),

  /** Est-ce suivi ? (public) */
  isFollowing: (followerId: number, channelId: number) =>
    http.get<boolean>(`/follows/is-following?followerId=${followerId}&channelId=${channelId}`),

  /** Chaînes suivies par un compte (paginé). */
  followedChannels: (followerId: number, page = 0, size = 20) =>
    http.get<ChannelPage>(`/follows/${followerId}/following?page=${page}&size=${size}`),

  /** Nombre d'abonnés d'une chaîne (public). */
  followersCount: (channelId: number) =>
    http.get<number>(`/follows/followers-count?channelId=${channelId}`),
}
