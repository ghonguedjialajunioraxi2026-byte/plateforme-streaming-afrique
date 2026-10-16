import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { aiApi } from './ai'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('aiApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('titles → /ai/creator/titles', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ suggestions: ['A', 'B', 'C'] })),
    )

    const data = await aiApi.titles({ topic: 'gaming' })

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/ai/creator/titles',
    )
    expect(data.suggestions).toHaveLength(3)
  })

  it('assistant → /ai/assistant', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ answer: 'Bonjour', sources: ['follow'] })),
    )

    const data = await aiApi.assistant('Comment suivre une chaîne ?')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/ai/assistant',
    )
    expect(data.sources).toContain('follow')
  })

  it('classify → /ai/moderation/classify', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ verdict: 'SAFE', confidence: 0.9 })),
    )

    const data = await aiApi.classify('bonjour')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/ai/moderation/classify',
    )
    expect(data.verdict).toBe('SAFE')
  })

  it('usage → /ai/admin/usage', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, envelope({ provider: 'stub', features: [] })),
    )

    const data = await aiApi.usage()

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/ai/admin/usage',
    )
    expect(data.provider).toBe('stub')
  })
})
