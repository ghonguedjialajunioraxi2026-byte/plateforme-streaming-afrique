import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adminApi } from './admin'
import { moderationApi } from './moderation'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('adminApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('dashboard → /admin/dashboard', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ usersTotal: 4 })),
    )

    const data = await adminApi.dashboard()

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/admin/dashboard',
    )
    expect(data.usersTotal).toBe(4)
  })

  it('users → recherche + filtres + pagination', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [] })),
    )

    await adminApi.users({ search: 'ada', role: 'CREATOR', status: 'ACTIVE', page: 1, size: 20 })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/admin/users?search=ada&role=CREATOR&status=ACTIVE&page=1&size=20',
    )
  })

  it('setUserStatus → PATCH avec motif (jamais sans)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ id: 9 })))

    await adminApi.setUserStatus(9, 'SUSPENDED', 'spam avéré')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/admin/users/9/status')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body as string)).toEqual({ status: 'SUSPENDED', reason: 'spam avéré' })
  })

  it('audit → filtres action/acteur', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [] })),
    )

    await adminApi.audit({ action: 'USER_STATUS_CHANGED', actorId: 1 })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/admin/audit?action=USER_STATUS_CHANGED&actorId=1&page=0&size=20',
    )
  })
})

describe('moderationApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('fileReport → POST /reports (type + id, jamais d’entité)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(201, envelope({ id: 1 })))

    await moderationApi.fileReport('CHAT_MESSAGE', 12, 'SPAM', 'lien douteux')

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/reports')
    expect(JSON.parse(init.body as string)).toEqual({
      targetType: 'CHAT_MESSAGE',
      targetId: 12,
      reason: 'SPAM',
      description: 'lien douteux',
    })
  })

  it('decide → POST /moderator/reports/{id}/decide', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(201, envelope({ id: 2 })))

    await moderationApi.decide(1, {
      action: 'TIMEOUT',
      scope: 'ACCOUNT',
      targetUserId: 9,
      reason: 'spam',
      endsAt: '2026-09-21T20:00:00Z',
    })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/moderator/reports/1/decide')
    expect(init.method).toBe('POST')
  })

  it('hideMessage → POST avec motif', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ id: 7 })))

    await moderationApi.hideMessage(7, 'spam confirmé')

    const [url] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/moderator/chat/7/hide')
  })
})
