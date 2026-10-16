import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { regionApi } from './region'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('regionApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('countries → /countries', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope([{ code: 'CM', name: 'Cameroun' }])),
    )
    const data = await regionApi.countries()
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/countries')
    expect(data[0].code).toBe('CM')
  })

  it('trending → filtres pays/langue transmis', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ streams: [], videos: [], emergingCreators: [] })),
    )
    await regionApi.trending({ country: 'NG', language: 'en', size: 8 })
    const url = String(fetchMock.mock.calls[0]?.[0])
    expect(url).toContain('/discovery/trending?')
    expect(url).toContain('country=NG')
    expect(url).toContain('language=en')
  })

  it('paymentMethods → /payment-methods?country=', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope([])))
    await regionApi.paymentMethods('CM')
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/payment-methods?country=CM',
    )
  })
})
