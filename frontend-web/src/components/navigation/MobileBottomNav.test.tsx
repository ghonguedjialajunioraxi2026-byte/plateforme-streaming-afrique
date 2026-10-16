import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { MobileBottomNav } from './MobileBottomNav'

vi.mock('../../services/api/notifications', () => ({
  notificationsApi: { unreadCount: vi.fn(async () => 0) },
}))

const GUEST = { user: null, isAuthenticated: false, logout: vi.fn() }
const AUTH = {
  user: { id: 7, username: 'ada', role: 'USER' },
  isAuthenticated: true,
  logout: vi.fn(),
}

vi.mock('../../features/auth/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../../features/auth/useAuth'

const mockedAuth = vi.mocked(useAuth)
type AuthValue = ReturnType<typeof useAuth>

function renderNav() {
  render(
    <MemoryRouter initialEntries={['/']}>
      <MobileBottomNav />
    </MemoryRouter>,
  )
}

describe('MobileBottomNav', () => {
  it('visiteur : 4 items dont Connexion', () => {
    mockedAuth.mockReturnValue(GUEST as unknown as AuthValue)
    renderNav()
    expect(screen.getByText('Accueil')).toBeVisible()
    expect(screen.getByText('Explorer')).toBeVisible()
    expect(screen.getByText('Recherche')).toBeVisible()
    expect(screen.getByText('Connexion')).toBeVisible()
    expect(screen.queryByLabelText('Créer un stream')).toBeNull()
  })

  it('connecté : Accueil, Explorer, Créer, Notifications, Profil', () => {
    mockedAuth.mockReturnValue(AUTH as unknown as AuthValue)
    renderNav()
    expect(screen.getByText('Accueil')).toBeVisible()
    expect(screen.getByText('Explorer')).toBeVisible()
    expect(screen.getByText('Créer')).toBeVisible()
    expect(screen.getByText('Notifications')).toBeVisible()
    expect(screen.getByText('Profil')).toBeVisible()
    expect(screen.getByLabelText('Créer un stream')).toHaveAttribute('href', '/creator/stream')
  })
})
