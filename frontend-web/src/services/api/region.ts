/// Service Africa+ (étape 19) — miroir de `RegionController`,
/// `RegionAdminController` et `DiscoveryController`.
/// Pays, langues, devises et moyens lus depuis la base (jamais hard-codés).
import { http } from '../http'

export interface Country {
  code: string
  name: string
  region: string
  phoneCode: string | null
  defaultCurrency: string
  defaultLocale: string
  supportedLanguages: string[]
  timezone: string
  active: boolean
}

export interface CountryPaymentMethod {
  id: number
  countryCode: string
  provider: string
  method: string
  currency: string
  active: boolean
  sandbox: boolean
}

export interface RegionConfig {
  features: Record<string, boolean>
  exchangeRatesAvailable: boolean
  activeCountries: string[]
}

export interface ScoredStream {
  stream: {
    id: number
    title: string
    creatorSlug: string | null
    categorySlug: string | null
    status: string
    language: string | null
    thumbnailUrl: string | null
    viewerCount: number
  }
  score: number
}

export interface ScoredVideo {
  video: {
    id: number
    title: string
    thumbnailUrl: string | null
    viewCount: number
    language: string | null
  }
  score: number
}

export interface EmergingCreator {
  id: number
  displayName: string
  slug: string
  avatarUrl: string | null
  followerCount: number
}

export interface Trending {
  streams: ScoredStream[]
  videos: ScoredVideo[]
  emergingCreators: EmergingCreator[]
}

function params(input: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined && value !== '') search.set(key, String(value))
  }
  const query = search.toString()
  return query === '' ? '' : `?${query}`
}

export const regionApi = {
  /** Pays actifs (public, caché 15 min côté backend). */
  countries: () => http.get<Country[]>('/countries'),

  /** Détail d'un pays (404 si inconnu). */
  country: (code: string) => http.get<Country>(`/countries/${code}`),

  /** Langues supportées (union des pays actifs). */
  locales: () => http.get<string[]>('/locales'),

  /** Devises des pays actifs. */
  currencies: () => http.get<string[]>('/currencies'),

  /** Moyens de paiement ACTIFS d'un pays (vides si non intégrés). */
  paymentMethods: (country: string) =>
    http.get<CountryPaymentMethod[]>(`/payment-methods${params({ country })}`),

  /** Configuration régionale (flags, taux, pays actifs). */
  config: () => http.get<RegionConfig>('/config/region'),

  /** Tendances (tous pays ou filtrées : country, language, category). */
  trending: (filters?: { country?: string; language?: string; category?: string; size?: number }) =>
    http.get<Trending>(`/discovery/trending${params(filters ?? {})}`),

  /** Tendances d'un pays (404 si code inconnu). */
  trendingByCountry: (country: string, filters?: { language?: string; category?: string; size?: number }) =>
    http.get<Trending>(`/discovery/trending/${country}${params(filters ?? {})}`),

  /** Créateurs émergents (croissance réelle, jamais de promesse). */
  emerging: (filters?: { country?: string; size?: number }) =>
    http.get<EmergingCreator[]>(`/discovery/creators/emerging${params(filters ?? {})}`),

  /** Activer/désactiver un pays (ADMIN). */
  setCountryActive: (code: string, active: boolean) =>
    http.put<Country>(`/admin/region/countries/${code}`, { active }),

  /** Moyens d'un pays, actifs + préparés (ADMIN). */
  allPaymentMethods: (code: string) =>
    http.get<CountryPaymentMethod[]>(`/admin/region/countries/${code}/payment-methods`),

  /** Préparer un moyen (inactif par défaut, ADMIN). */
  addPaymentMethod: (code: string, input: { provider: string; method: string; currency: string }) =>
    http.post<CountryPaymentMethod>(`/admin/region/countries/${code}/payment-methods`, input),

  /** Activer/désactiver un moyen (ADMIN — exige une intégration réelle). */
  setMethodActive: (id: number, active: boolean) =>
    http.put<CountryPaymentMethod>(`/admin/region/payment-methods/${id}`, { active }),
}
