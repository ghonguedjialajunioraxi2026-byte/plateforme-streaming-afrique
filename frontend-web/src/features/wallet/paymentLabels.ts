/// Labels et formatage des paiements (étape 14) — français, partagés par
/// les pages wallet et admin. Les statuts affichés reprennent le vocabulaire
/// serveur (INITIATED/PENDING/CONFIRMED/…) sans le réinterpréter.

export const PROVIDER_LABELS: Record<string, string> = {
  WAVE: 'Wave',
  ORANGE_MONEY: 'Orange Money',
  MTN_MOMO: 'MTN MoMo',
  MPESA: 'M-Pesa',
  STRIPE: 'Stripe',
  PAYSTACK: 'Paystack',
  FLUTTERWAVE: 'Flutterwave',
  OTHER: 'Autre prestataire',
}

export const METHOD_LABELS: Record<string, string> = {
  MOBILE_MONEY: 'Mobile Money',
  CARD: 'Carte bancaire',
  BANK_TRANSFER: 'Virement bancaire',
  OTHER: 'Autre moyen',
}

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  INITIATED: 'Initié',
  PENDING: 'En attente',
  CONFIRMED: 'Confirmé',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
  EXPIRED: 'Expiré',
  REFUNDED: 'Remboursé',
}

export type BadgeVariant = 'live' | 'primary' | 'accent' | 'neutral' | 'danger' | 'warning'

export function paymentStatusVariant(status: string): BadgeVariant {
  switch (status) {
    case 'CONFIRMED':
      return 'live'
    case 'FAILED':
      return 'danger'
    case 'PENDING':
    case 'INITIATED':
    case 'EXPIRED':
      return 'warning'
    case 'REFUNDED':
      return 'accent'
    default:
      return 'neutral'
  }
}

/** États d'attente : le frontend sonde, le backend reste la vérité. */
export function isPaymentPending(status: string): boolean {
  return status === 'INITIATED' || status === 'PENDING'
}

export function providerLabel(provider: string): string {
  return PROVIDER_LABELS[provider] ?? provider
}

export function methodLabel(method: string): string {
  return METHOD_LABELS[method] ?? method
}

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status
}

/** 5000 → "5 000 coins" (espaces insécables, français). */
export function formatCoins(amount: number): string {
  return `${amount.toLocaleString('fr-FR')} coins`
}

/** 5000 XOF → "5 000 XOF". */
export function formatMoney(amount: number, currency: string): string {
  const normalized = typeof amount === 'number' ? amount : Number(amount)
  return `${normalized.toLocaleString('fr-FR')} ${currency}`
}

export function formatDate(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
