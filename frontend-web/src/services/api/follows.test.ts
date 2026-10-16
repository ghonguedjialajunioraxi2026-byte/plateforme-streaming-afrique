import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { followsApi } from './follows'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('followsApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('follow → POST /follows (corps followerId + channelId)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(201, envelope({ id: 1, followerId: 4, followedId: 2, createdAt: '2026-01-01T00:00:00Z' })),
    )

    const data = await followsApi.follow(4, 2)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/follows')
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST' })
    expect(data.followedId).toBe(2)
  })

  it('unfollow → DELETE /follows?followerId=&channelId=', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope(null)))

    await followsApi.unfollow(4, 2)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/follows?followerId=4&channelId=2',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'DELETE' })
  })

  it('isFollowing → GET /follows/is-following (booléen)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope(true)))

    const value = await followsApi.isFollowing(4, 2)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/follows/is-following?followerId=4&channelId=2',
    )
    expect(value).toBe(true)
  })

  it('followedChannels → GET /follows/{id}/following (paginé)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
    )

    const page = await followsApi.followedChannels(4, 0, 20)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/follows/4/following?page=0&size=20',
    )
    expect(page.content).toHaveLength(0)
  })
})
