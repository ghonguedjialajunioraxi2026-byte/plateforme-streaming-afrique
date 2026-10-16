/// Service portefeuille (étape 14) — miroir RÉEL de `WalletController` backend.
///
/// Lecture seule : le solde naît du webhook (achats), jamais d'écriture ici.
import { http } from '../http'

export interface Wallet {
  balanceCoins: number
  reservedCoins: number
  availableCoins: number
  status: string
}

export interface WalletMovement {
  id: number
  direction: 'CREDIT' | 'DEBIT'
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

export interface WalletMovementPage {
  content: WalletMovement[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const walletApi = {
  /** Mon solde (créé vide à la première lecture côté backend). */
  me: () => http.get<Wallet>('/wallet/me'),

  /** Mon journal (récent d'abord). */
  transactions: (page = 0, size = 10) =>
    http.get<WalletMovementPage>(`/wallet/transactions?page=${page}&size=${size}`),
}
