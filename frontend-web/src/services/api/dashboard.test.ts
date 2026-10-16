import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { creatorDashboardApi } from './dashboard'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('creatorDashboardApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('overview → /creator/dashboard (aucun id envoyé, JWT seul)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ channel: { id: 1 }, live: null })),
    )

    const data = await creatorDashboardApi.overview()

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/creator/dashboard',
    )
    expect(data.channel.id).toBe(1)
  })

  it('analytics → /creator/analytics?days= (1..90)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ periodDays: 7, series: [] })),
    )

    await creatorDashboardApi.analytics(7)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/creator/analytics?days=7',
    )
  })

  it('revenue / community / moderation → routes dédiées', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>

    await creatorDashboardApi.revenue()
    await creatorDashboardApi.community()
    await creatorDashboardApi.moderation()

    const urls = fetchMock.mock.calls.map((call) => call[0])
    expect(urls).toEqual([
      'http://localhost:8080/api/creator/revenue',
      'http://localhost:8080/api/creator/community',
      'http://localhost:8080/api/creator/moderation',
    ])
  })
})
