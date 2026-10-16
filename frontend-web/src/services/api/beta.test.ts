import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { betaApi } from './beta'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('betaApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('submitFeedback → POST /beta/feedback (201)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ id: 1, status: 'OPEN' })),
    )

    const created = await betaApi.submitFeedback({
      category: 'BUG',
      title: 'Player figé',
      description: 'Détails',
      route: '/live/3',
      appVersion: 'beta-0.1.0',
    })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/beta/feedback',
    )
    expect(created.status).toBe('OPEN')
  })

  it('invite → POST /admin/beta/invitations (code montré une fois)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ id: 2, code: 'FLICK-BETA-X' })),
    )

    const created = await betaApi.invite('testeur@example.com', 'note')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/admin/beta/invitations',
    )
    expect(created.code).toBe('FLICK-BETA-X')
  })

  it('triage → PUT statut + priorité', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ id: 3, status: 'IN_PROGRESS', priority: 'P1' })),
    )

    const triaged = await betaApi.triage(3, 'IN_PROGRESS', 'P1')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/admin/beta/feedback/3/triage',
    )
    expect(triaged.priority).toBe('P1')
  })
})
