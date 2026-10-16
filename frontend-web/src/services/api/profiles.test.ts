import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { profilesApi } from './profiles'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('profilesApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getMe → /profiles/me', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ username: 'ada' })))

    const profile = await profilesApi.getMe()

    expect(profile.username).toBe('ada')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/profiles/me')
  })

  it('getPublic → pseudo encodé dans l URL', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ username: 'ada' })))

    await profilesApi.getPublic('ada lovelace')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/profiles/username/ada%20lovelace',
    )
  })

  it('uploadAvatar → multipart SANS Content-Type JSON', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ username: 'ada' })))
    const file = new File(['x'], 'a.png', { type: 'image/png' })

    await profilesApi.uploadAvatar(file)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/profiles/me/avatar')
    expect(init.method).toBe('POST')
    expect(init.body).toBeInstanceOf(FormData)
    expect(init.headers).not.toHaveProperty('Content-Type')
  })
})
