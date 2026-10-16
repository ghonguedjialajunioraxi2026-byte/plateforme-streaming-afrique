import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider } from './AuthContext'
import { useAuth } from './useAuth'
import {
  getRefreshToken,
  setAccessToken,
  setAuthExpiredHandler,
  setRefreshToken,
} from '../../services/http'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function envelope<T>(data: T): object {
  return { success: true, message: 'ok', data, timestamp: '2026-01-01T00:00:00Z' }
}

const ME = {
  id: 7,
  username: 'user237',
  email: 'user@example.com',
  displayName: 'User 237',
  role: 'USER',
  emailVerified: false,
}

function Probe() {
  const { user, isAuthenticated, isLoading, login, loginWithGoogle, logout, refreshSession } = useAuth()
  return (
    <div>
      <span data-testid="state">
        {isLoading ? 'loading' : isAuthenticated ? `in:${user?.username}:${user?.role}` : 'out'}
      </span>
      <button type="button" onClick={() => void login('user@example.com', 'MotDePasseFort123!')}>
        login
      </button>
      <button type="button" onClick={() => void loginWithGoogle('GOOGLE_ID_TOKEN')}>
        google
      </button>
      <button type="button" onClick={() => void logout()}>
        logout
      </button>
      <button type="button" onClick={() => void refreshSession()}>
        refresh-session
      </button>
    </div>
  )
}

describe('AuthContext', () => {
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

  it('anonyme sans refresh persisté (récupération + état)', async () => {
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )

    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })
  })

  it('login → tokens stockés + utilisateur chargé', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    let loggedIn = false
    fetchMock.mockImplementation(async (url: unknown) => {
      const path = String(url)
      if (path.endsWith('/auth/login')) {
        loggedIn = true
        return jsonResponse(
          200,
          envelope({
            accessToken: 'ACCESS',
            tokenType: 'Bearer',
            expiresIn: 900,
            refreshToken: 'REFRESH',
            refreshExpiresIn: 604800,
            userId: 7,
            email: 'user@example.com',
            displayName: 'User 237',
            role: 'USER',
          }),
        )
      }
      if (path.endsWith('/auth/me') && loggedIn) {
        return jsonResponse(200, envelope(ME))
      }
      return jsonResponse(401, { message: 'nope' })
    })
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })

    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('in:user237:USER')
    })
    expect(getRefreshToken()).toBe('REFRESH')
  })

  it('logout → révocation serveur + purge locale', async () => {    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    const calls: string[] = []
    let loggedIn = false
    fetchMock.mockImplementation(async (url: unknown, init?: RequestInit) => {
      calls.push(`${init?.method ?? 'GET'} ${String(url)}`)
      const path = String(url)
      if (path.endsWith('/auth/login')) {
        loggedIn = true
        return jsonResponse(
          200,
          envelope({
            accessToken: 'ACCESS',
            tokenType: 'Bearer',
            expiresIn: 900,
            refreshToken: 'REFRESH',
            refreshExpiresIn: 604800,
            userId: 7,
            email: 'user@example.com',
            displayName: 'User 237',
            role: 'USER',
          }),
        )
      }
      if (path.endsWith('/auth/logout')) {
        return jsonResponse(200, envelope(null))
      }
      if (path.endsWith('/auth/me') && loggedIn) {
        return jsonResponse(200, envelope(ME))
      }
      return jsonResponse(401, { message: 'nope' })
    })
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })
    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('in:user237:USER')
    })

    fireEvent.click(screen.getByRole('button', { name: 'logout' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })
    expect(getRefreshToken()).toBeNull()
    expect(calls.some((call) => call.includes('/auth/logout'))).toBe(true)
  })

  it('login Google → id_token posté + session FLICK établie', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    const calls: unknown[] = []
    let loggedIn = false
    fetchMock.mockImplementation(async (url: unknown, init?: RequestInit) => {
      calls.push(init?.body)
      const path = String(url)
      if (path.endsWith('/auth/google')) {
        loggedIn = true
        return jsonResponse(
          200,
          envelope({
            accessToken: 'ACCESS_GOOGLE',
            tokenType: 'Bearer',
            expiresIn: 900,
            refreshToken: 'REFRESH_GOOGLE',
            refreshExpiresIn: 604800,
            userId: 7,
            email: 'user@example.com',
            displayName: 'User 237',
            role: 'USER',
          }),
        )
      }
      if (path.endsWith('/auth/me') && loggedIn) {
        return jsonResponse(200, envelope(ME))
      }
      return jsonResponse(401, { message: 'nope' })
    })
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })

    fireEvent.click(screen.getByRole('button', { name: 'google' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('in:user237:USER')
    })
    expect(getRefreshToken()).toBe('REFRESH_GOOGLE')
    expect(calls.some((body) => String(body).includes('GOOGLE_ID_TOKEN'))).toBe(true)
  })

  it('refreshSession → rotation + rôle serveur répercuté (ouverture de chaîne)', async () => {
    const fetchMock = fetch as unknown as ReturnType<typeof vi.fn>
    let role: 'USER' | 'CREATOR' = 'USER'
    let loggedIn = false
    fetchMock.mockImplementation(async (url: unknown) => {
      const path = String(url)
      if (path.endsWith('/auth/login')) {
        loggedIn = true
        return jsonResponse(
          200,
          envelope({
            accessToken: 'ACCESS',
            tokenType: 'Bearer',
            expiresIn: 900,
            refreshToken: 'REFRESH',
            refreshExpiresIn: 604800,
            userId: 7,
            email: 'user@example.com',
            displayName: 'User 237',
            role,
          }),
        )
      }
      if (path.endsWith('/auth/refresh')) {
        role = 'CREATOR'
        return jsonResponse(
          200,
          envelope({
            accessToken: 'ACCESS_V2',
            tokenType: 'Bearer',
            expiresIn: 900,
            refreshToken: 'REFRESH_V2',
            refreshExpiresIn: 604800,
          }),
        )
      }
      if (path.endsWith('/auth/me') && loggedIn) {
        return jsonResponse(200, envelope({ ...ME, role }))
      }
      return jsonResponse(401, { message: 'nope' })
    })
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('out')
    })
    fireEvent.click(screen.getByRole('button', { name: 'login' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('in:user237:USER')
    })
    fireEvent.click(screen.getByRole('button', { name: 'refresh-session' }))
    await waitFor(() => {
      expect(screen.getByTestId('state')).toHaveTextContent('in:user237:CREATOR')
    })
    expect(getRefreshToken()).toBe('REFRESH_V2')
  })
})
