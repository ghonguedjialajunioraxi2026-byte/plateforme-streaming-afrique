/// Service paiements (étape 14) — miroir RÉEL de `PaymentController`,
/// `CoinPackageController` et `PaymentWebhookController` backend.
///
/// Règle serveur : le frontend n'envoie QUE l'intention (packageId +
/// prestataire + moyen + clé d'idempotence). Prix, coins, devise, payeur
/// et statut sont déterminés côté serveur — aucun champ de ce type ici.
import { http } from '../http'

export interface CoinPackage {
  id: number
  name: string
  coinsAmount: number
  priceAmount: number
  priceCurrency: string
  active: boolean
  sortOrder: number
  description: string | null
}

export interface Payment {
  id: number
  internalRef: string
  providerRef: string | null
  provider: string
  method: string
  status: string
  fiatAmount: number
  fiatCurrency: string
  coinsAmount: number
  idempotencyKey: string | null
  paymentActionType: string | null
  paymentActionUrl: string | null
  paymentInstructions: string | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export interface PaymentProvider {
  provider: string
  methods: string[]
  sandbox: boolean
}

export interface PaymentPage {
  content: Payment[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface CreatePaymentInput {
  packageId: number
  provider: string
  method: string
  idempotencyKey?: string
}

/** UUID v4 (clé d'idempotence : une par intention d'achat). */
export function newIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.floor(Math.random() * 1e9)}`
}

export const paymentsApi = {
  /** Vitrine d'achat (packs actifs triés, filtre devise ISO-3 optionnel). */
  listPackages: (currency?: string) =>
    http.get<CoinPackage[]>(
      currency ? `/coin-packages?currency=${encodeURIComponent(currency)}` : '/coin-packages',
    ),

  /** Prestataires EFFECTIVEMENT disponibles (le frontend n'affiche que ceux-ci). */
  listProviders: () => http.get<PaymentProvider[]>('/payments/providers'),

  /** Créer un paiement (201 ; rejeu même clé = même paiement). */
  create: (input: CreatePaymentInput) => http.post<Payment>('/payments', input),

  /** Statut réel côté serveur (sondage après l'action de paiement). */
  get: (id: number) => http.get<Payment>(`/payments/${id}`),

  /** Annuler avant confirmation. */
  cancel: (id: number) => http.post<Payment>(`/payments/${id}/cancel`, {}),

  /** Mon historique (récents d'abord). */
  myHistory: (page = 0, size = 10) =>
    http.get<PaymentPage>(`/payments/mine?page=${page}&size=${size}`),

  /** Simulateur sandbox (dev/test : rejoue le pipeline webhook réel). */
  simulate: (internalRef: string, outcome: 'SUCCEEDED' | 'FAILED' | 'PENDING') =>
    http.post<string>('/payments/sandbox/simulate', { internalRef, outcome }),

  /** Supervision (ADMIN). */
  adminAll: (page = 0, size = 20) =>
    http.get<PaymentPage>(`/payments/admin/all?page=${page}&size=${size}`),
}
