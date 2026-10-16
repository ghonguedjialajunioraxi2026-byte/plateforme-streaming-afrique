/// Service IA (étape 18) — miroir RÉEL de `AiController`.
/// Le frontend n'appelle JAMAIS un fournisseur IA directement :
/// tout passe par le backend (clé API jamais exposée).
import { http } from '../http'

export interface TitleSuggestions {
  suggestions: string[]
  provider: string
  model: string
}

export interface TextProposal {
  proposal: string
  provider: string
  model: string
}

export interface TagSuggestions {
  tags: string[]
  provider: string
  model: string
}

export interface VodSummary {
  summary: string
  improvedDescription: string | null
  keyPoints: string[]
  transcriptAvailable: boolean
  provider: string
  model: string
}

export interface AnalyticsAdvice {
  analysis: string
  suggestions: string[]
  dataSufficient: boolean
  provider: string
  model: string
}

export interface AssistantAnswer {
  answer: string
  sources: string[]
  provider: string
  model: string
}

export interface SearchAssist {
  keywords: string[]
  category: string
  region: string
  searchQuery: string
  provider: string
  model: string
}

export interface Classification {
  verdict: 'SAFE' | 'SUSPICIOUS' | 'VIOLATION'
  confidence: number
  category: string
  reason: string
  humanReviewRequired: boolean
  provider: string
  model: string
}

export interface AiFeatureUsage {
  feature: string
  requests: number
  tokens: number
  avgLatencyMs: number
  errors: number
}

export interface AiErrorEntry {
  id: number
  feature: string
  status: string
  errorCode: string | null
  createdAt: string | null
}

export interface AiUsageStats {
  provider: string
  model: string
  enabled: boolean
  features: AiFeatureUsage[]
  recentErrors: AiErrorEntry[]
}

export const aiApi = {
  /** 3 titres proposés (CREATOR+). Proposition — jamais appliquée seule. */
  titles: (input: { topic: string; category?: string; description?: string; audience?: string; style?: string }) =>
    http.post<TitleSuggestions>('/ai/creator/titles', input),

  /** Rédiger / améliorer une description (mode: create | improve). */
  description: (input: { text: string; mode?: string }) =>
    http.post<TextProposal>('/ai/creator/descriptions', input),

  /** Tags proposés depuis titre + description + catégorie. */
  tags: (input: { title: string; description?: string; category?: string }) =>
    http.post<TagSuggestions>('/ai/creator/tags', input),

  /** Résumé de VOD (métadonnées uniquement, VOD du demandeur). */
  vodSummary: (videoId: number) =>
    http.get<VodSummary>(`/ai/creator/vod/${videoId}/summary`),

  /** Tranches de clips indicatives (sans fausse détection). */
  clipSuggestions: (videoId: number) =>
    http.get<VodSummary>(`/ai/creator/vod/${videoId}/clip-suggestions`),

  /** Conseil analytics sur chiffres réels. */
  analytics: (question: string) =>
    http.post<AnalyticsAdvice>('/ai/creator/analytics', { question }),

  /** Assistant Flick (tout connecté, base de connaissances officielle). */
  assistant: (question: string) =>
    http.post<AssistantAnswer>('/ai/assistant', { question }),

  /** Intention de recherche → réutiliser le moteur existant. */
  searchAssist: (query: string) =>
    http.post<SearchAssist>('/ai/search-assist', { query }),

  /** Classifier un message (MODERATOR+, recommandation seule). */
  classify: (text: string) =>
    http.post<Classification>('/ai/moderation/classify', { text }),

  /** Monitoring IA (ADMIN). */
  usage: () => http.get<AiUsageStats>('/ai/admin/usage'),

  /** Fonctionnalités IA + états (ADMIN). */
  features: () => http.get<{ content: string[]; totalElements: number }>('/ai/admin/features'),
}
