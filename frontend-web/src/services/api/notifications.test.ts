import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { notificationsApi } from './notifications'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

describe('notificationsApi', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(200, envelope(null))),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('list → GET /notifications?recipientId= (paginé)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        200,
        envelope({ content: [{ id: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      ),
    )

    const page = await notificationsApi.list(4, 0, 20)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/notifications?recipientId=4&page=0&size=20',
    )
    expect(page.totalElements).toBe(1)
  })

  it('unreadCount → GET /notifications/unread-count', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope(3)))

    const count = await notificationsApi.unreadCount(4)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/notifications/unread-count?recipientId=4',
    )
    expect(count).toBe(3)
  })

  it('markAsRead → PATCH /notifications/{id}/read', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope({ id: 7, isRead: true })))

    const item = await notificationsApi.markAsRead(7)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/notifications/7/read',
    )
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'PATCH' })
    expect(item.isRead).toBe(true)
  })

  it('markAllAsRead → POST /notifications/{id}/read-all', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockResolvedValueOnce(jsonResponse(200, envelope(5)))

    const count = await notificationsApi.markAllAsRead(4)

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      'http://localhost:8080/api/notifications/4/read-all',
    )
    expect(count).toBe(5)
  })
})
