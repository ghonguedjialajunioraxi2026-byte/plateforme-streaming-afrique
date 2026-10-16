import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clipsApi } from './clips'
import { videosApi } from './videos'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('videosApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('feed → /videos/feed avec pagination', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [], totalElements: 0 })),
    )

    await videosApi.feed('tuto', 'gaming', 1, 12)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/videos/feed?search=tuto&category=gaming&page=1&size=12',
    )
  })

  it('recordView → POST /videos/{id}/view', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope(42)))

    const count = await videosApi.recordView(7)

    expect(count).toBe(42)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/videos/7/view')
    expect(init.method).toBe('POST')
  })

  it('createClip → intention seule (titre + bornes, jamais de durée imposée)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(201, envelope({ id: 3 })))

    await videosApi.createClip(9, { title: 'Moment', startTimeSeconds: 100, durationSeconds: 20 })

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('http://localhost:8080/api/videos/9/clips')
    expect(JSON.parse(init.body as string)).toEqual({
      title: 'Moment',
      startTimeSeconds: 100,
      durationSeconds: 20,
    })
  })
})

describe('clipsApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('feed → /clips/feed', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ content: [], totalElements: 0 })),
    )

    await clipsApi.feed(undefined, 0, 12)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/clips/feed?page=0&size=12',
    )
  })

  it('playback → /clips/{id}/playback', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ playbackUrl: 'https://cdn.example.com/c.mp4' })),
    )

    const playback = await clipsApi.playback(5)

    expect(playback.playbackUrl).toBe('https://cdn.example.com/c.mp4')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://localhost:8080/api/clips/5/playback')
  })
})
