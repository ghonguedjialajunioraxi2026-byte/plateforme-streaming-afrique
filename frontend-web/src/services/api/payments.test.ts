import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { newIdempotencyKey, paymentsApi } from './payments'
import { walletApi } from './wallet'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('paymentsApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('listPackages → /coin-packages', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope([{ id: 1, name: 'Pack Starter' }])),
    )

    const packs = await paymentsApi.listPackages()

    expect(packs[0]?.name).toBe('Pack Starter')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/coin-packages')
  })

  it('create → intention seule (packageId, provider, method, idempotencyKey)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(201, envelope({ id: 7 })))

    await paymentsApi.create({
      packageId: 3,
      provider: 'WAVE',
      method: 'MOBILE_MONEY',
      idempotencyKey: 'cle-123',
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/payments')
    expect(init.method).toBe('POST')
    const sent = JSON.parse(init.body as string) as Record<string, unknown>
    // Aucun prix/coins/devise/userId : le serveur les détermine (§17).
    expect(sent).toEqual({
      packageId: 3,
      provider: 'WAVE',
      method: 'MOBILE_MONEY',
      idempotencyKey: 'cle-123',
    })
  })

  it('get → statut réel /payments/{id}', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ status: 'CONFIRMED' })))

    const payment = await paymentsApi.get(7)

    expect(payment.status).toBe('CONFIRMED')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/payments/7')
  })

  it('newIdempotencyKey → identifiant unique non vide', () => {
    expect(newIdempotencyKey()).not.toBe(newIdempotencyKey())
  })
})

describe('walletApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('me → /wallet/me', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ balanceCoins: 5000 })))

    const wallet = await walletApi.me()

    expect(wallet.balanceCoins).toBe(5000)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/wallet/me')
  })

  it('transactions → /wallet/transactions paginé', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [], totalElements: 0 })),
    )

    await walletApi.transactions(1, 10)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/wallet/transactions?page=1&size=10',
    )
  })
})
