import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  getRefreshToken,
  setAccessToken,
  setAuthExpiredHandler,
  setRefreshToken,
  http,
} from './http'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('http', () => {
  beforeEach(() => {
    localStorage.clear()
    setAccessToken(null)
    setRefreshToken(null)
    setAuthExpiredHandler(null)
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
    setAccessToken(null)
    setRefreshToken(null)
    setAuthExpiredHandler(null)
  })

  it('déballe data et envoie le Bearer en mémoire', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ id: 1 })))
    setAccessToken('ACCESS')

    const data = await http.get<{ id: number }>('/auth/me')

    expect(data).toEqual({ id: 1 })
    expect(fetchMock).toHaveBeenCalledOnce()
    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer ACCESS')
  })

  it('lève ApiError avec code métier sur erreur', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        success: false,
        status: 409,
        errorCode: 'CONFLICT',
        message: 'Déjà pris',
        path: '/api/auth/register',
        timestamp: '2026-01-01T00:00:00Z',
      }),
    )

    const failure = await http.post('/auth/register', {}).catch((error: unknown) => error)
    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as ApiError).status).toBe(409)
    expect((failure as ApiError).errorCode).toBe('CONFLICT')
    expect((failure as ApiError).message).toBe('Déjà pris')
  })

  it('401 → refresh single-flight → retry unique', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    let meCalls = 0
    fetchMock.mockImplementation(async (url: unknown) => {
      const path = String(url)
      if (path.endsWith('/auth/refresh')) {
        return jsonResponse(
          200,
          envelope({ accessToken: 'NEW', refreshToken: 'REFRESH2' }),
        )
      }
      meCalls += 1
      // Premier passage : 401 (déclenche le refresh) ; retry : 200.
      if (meCalls <= 2) {
        return jsonResponse(401, { message: 'expired' })
      }
      return jsonResponse(200, envelope({ id: 7 }))
    })
    setAccessToken('OLD')
    setRefreshToken('REFRESH1')

    const [first, second] = await Promise.all([
      http.get<{ id: number }>('/auth/me'),
      http.get<{ id: number }>('/auth/me'),
    ])

    expect(first).toEqual({ id: 7 })
    expect(second).toEqual({ id: 7 })
    // 2×401 + 1 refresh + 2 retry = 5 appels, UN seul /auth/refresh.
    expect(fetchMock).toHaveBeenCalledTimes(5)
    const refreshCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).endsWith('/auth/refresh'),
    )
    expect(refreshCalls).toHaveLength(1)
    expect(getRefreshToken()).toBe('REFRESH2')
  })

  it('jamais de refresh sur /auth/login (pas de boucle)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(401, { message: 'nope' }))
    setRefreshToken('REFRESH1')

    await expect(http.post('/auth/login', {})).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  it('refresh échoué → purge + handler appelé', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { message: 'x' }))
      .mockResolvedValueOnce(jsonResponse(400, { message: 'bad refresh' }))
    setAccessToken('OLD')
    setRefreshToken('REFRESH1')
    const handler = vi.fn()
    setAuthExpiredHandler(handler)

    await expect(http.get('/auth/me')).rejects.toBeInstanceOf(ApiError)
    expect(getRefreshToken()).toBeNull()
    expect(handler).toHaveBeenCalledOnce()
  })
})
