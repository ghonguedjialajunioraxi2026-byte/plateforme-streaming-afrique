/// Utilitaire de test : simule un utilisateur connecté pour les pages qui
/// consomment `useAuth` (restauration via GET /auth/me réel, non mocké).
import { vi } from 'vitest'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

export interface MockUser {
  id: number
  username: string
  email: string
  displayName: string
  role: string
}

export const TEST_USER: MockUser = {
  id: 7,
  username: 'ada',
  email: 'ada@example.com',
  displayName: 'Ada',
  role: 'USER',
}

/** Stub `fetch` : `/auth/me` → 200 utilisateur, le reste → 401. */
export function mockAuthenticatedFetch(user: MockUser = TEST_USER): void {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: unknown) => {
      if (String(url).endsWith('/auth/me')) {
        return jsonResponse(200, envelope({ ...user, emailVerified: false }))
      }
      return jsonResponse(401, { message: 'nope' })
    }),
  )
}
