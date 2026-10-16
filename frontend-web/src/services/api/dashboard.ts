/// Service Creator Studio (étape 16) — miroir RÉEL de `DashboardController` backend.
///
/// Agrégats de la chaîne DU CONNECTÉ (JWT → compte → chaîne) : aucun
/// creatorId/userId n'est envoyé — l'accès au dashboard d'autrui est
/// impossible par construction. Chaque chiffre vient du backend
/// (agrégations SQL / compteurs Redis) ; le frontend n'invente rien.
import { http } from '../http'
import type { Clip, Video } from './videos'

export interface DashboardChannel {
  id: number
  displayName: string
  slug: string
  avatarUrl: string | null
  status: string
}

export interface DashboardLive {
  id: number
  title: string
  viewers: number
  peakViewers: number
  startedAt: string
}

export interface DashboardNotification {
  id: number
  type: string
  title: string
  message: string
  linkUrl: string | null
  isRead: boolean
  createdAt: string
}

export interface DashboardWallet {
  balanceCoins: number
  reservedCoins: number
  availableCoins: number
  status: string
}

export interface CreatorDashboard {
  channel: DashboardChannel
  live: DashboardLive | null
  followers: number
  subscribers: number
  livesTotal: number
  videosTotal: number
  videoViews: number
  clipsTotal: number
  clipViews: number
  giftsReceived: number
  giftCoinsEarned: number
  unreadNotifications: number
  wallet: DashboardWallet | null
  recentActivity: DashboardNotification[]
}

export interface AnalyticsSeriesPoint {
  metric: string
  periodStart: string
  value: number
}

export interface CreatorAnalytics {
  periodDays: number
  from: string
  to: string
  videoViews: number
  clipViews: number
  followers: number
  subscribers: number
  lives: number
  giftsReceived: number
  giftCoinsEarned: number
  series: AnalyticsSeriesPoint[]
  topVideos: Video[]
  topClips: Clip[]
}

export interface RevenueMovement {
  id: number
  direction: string
  amountCoins: number
  fiatReferenceAmount: number | null
  fiatCurrency: string | null
  balanceBefore: number
  balanceAfter: number
  kind: string
  status: string
  reference: string | null
  createdAt: string
}

export interface CreatorRevenue {
  wallet: DashboardWallet | null
  giftCoinsReceived: number
  subscriptionCoinsReceived: number
  totalCoinsReceived: number
  giftsReceived: number
  recentTransactions: RevenueMovement[]
}

export interface CommunityMember {
  userId: number
  username: string
  displayName: string | null
  avatarUrl: string | null
  followedAt: string
}

export interface CommunitySubscriber {
  userId: number
  displayName: string | null
  planName: string | null
  since: string
}

export interface CreatorCommunity {
  followersCount: number
  subscribersCount: number
  recentFollowers: CommunityMember[]
  recentSubscribers: CommunitySubscriber[]
}

export interface ModerationReport {
  id: number
  reason: string | null
  description: string | null
  status: string | null
  createdAt: string
}

export interface CreatorModeration {
  openReports: number
  recentOpenReports: ModerationReport[]
}

export const creatorDashboardApi = {
  /** Vue d'ensemble (cartes, live courant, activité). */
  overview: () => http.get<CreatorDashboard>('/creator/dashboard'),

  /** Analytique (totaux + courbes + top contenus, 1..90 jours). */
  analytics: (days = 30) =>
    http.get<CreatorAnalytics>(`/creator/analytics?days=${days}`),

  /** Revenus coins (soldes + totaux par source + journal). */
  revenue: () => http.get<CreatorRevenue>('/creator/revenue'),

  /** Communauté (followers + abonnés récents). */
  community: () => http.get<CreatorCommunity>('/creator/community'),

  /** Modération de son espace (signalements ouverts). */
  moderation: () => http.get<CreatorModeration>('/creator/moderation'),
}
