import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import ProtectedRoute from './ProtectedRoute'
import { AuthProvider } from '../features/auth/AuthContext'
import {
  setAccessToken,
  setAuthExpiredHandler,
  setRefreshToken,
} from '../services/http'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

function meWithRole(role: string): object {
  return {
    id: 7,
    username: 'user237',
    email: 'user@example.com',
    displayName: 'User 237',
    role,
    emailVerified: false,
  }
}

/** Routes minimales : publique, privée, restreinte ADMIN. */
describe('ProtectedRoute', () => {
  beforeEach(() => {
    localStorage.clear()
    setAccessToken(null)
    setRefreshToken(null)
    setAuthExpiredHandler(null)
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(401, { message: 'nope' })),
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    localStorage.clear()
    setAccessToken(null)
    setRefreshToken(null)
    setAuthExpiredHandler(null)
  })

  function renderAt(path: '/prive' | '/admin', role: string | null) {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    fetchMock.mockImplementation(async (url: unknown) => {
      if (String(url).endsWith('/auth/me')) {
        if (role === null) {
          return jsonResponse(401, { message: 'nope' })
        }
        return jsonResponse(200, envelope(meWithRole(role)))
      }
      return jsonResponse(401, { message: 'nope' })
    })
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/connexion" element={<p>page connexion</p>} />
            <Route path="/" element={<p>page accueil</p>} />
            <Route
              path="/prive"
              element={
                <ProtectedRoute>
                  <p>page privée</p>
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute allowedRoles={['ADMIN']}>
                  <p>page admin</p>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>,
      </AuthProvider>,
    )
  }

  it('anonyme → /connexion', async () => {
    renderAt('/prive', null)
    await waitFor(() => {
      expect(screen.getByText('page connexion')).toBeInTheDocument()
    })
  })

  it('USER connecté → page privée OK, /admin redirigé', async () => {
    renderAt('/prive', 'USER')
    await waitFor(() => {
      expect(screen.getByText('page privée')).toBeInTheDocument()
    })
  })

  it('rôle USER sur route ADMIN → accueil (backend seul fait foi)', async () => {
    renderAt('/admin', 'USER')
    await waitFor(() => {
      expect(screen.getByText('page accueil')).toBeInTheDocument()
    })
  })

  it('rôle ADMIN sur route ADMIN → page admin', async () => {
    renderAt('/admin', 'ADMIN')
    await waitFor(() => {
      expect(screen.getByText('page admin')).toBeInTheDocument()
    })
  })

  it('rôle CREATOR sur route ADMIN → accueil', async () => {
    renderAt('/admin', 'CREATOR')
    await waitFor(() => {
      expect(screen.getByText('page accueil')).toBeInTheDocument()
    })
  })
})
