import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { MobileHeader } from './MobileHeader'
import { ThemeProvider } from '../../features/theme/ThemeContext'

vi.mock('../../services/api/notifications', () => ({
  notificationsApi: { unreadCount: vi.fn(async () => 3) },
}))

vi.mock('../../features/auth/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../../features/auth/useAuth'

const mockedAuth = vi.mocked(useAuth)
type AuthValue = ReturnType<typeof useAuth>

function renderHeader() {
  render(
    <MemoryRouter initialEntries={['/']}>
      <ThemeProvider>
        <MobileHeader />
      </ThemeProvider>
    </MemoryRouter>,
  )
}

describe('MobileHeader', () => {
  it('visiteur : logo, recherche et bouton Connexion', () => {
    mockedAuth.mockReturnValue({ user: null, isAuthenticated: false, logout: vi.fn() } as unknown as AuthValue)
    renderHeader()
    expect(screen.getByLabelText('Rechercher')).toHaveAttribute('href', '/recherche')
    expect(screen.getByText('Connexion')).toHaveAttribute('href', '/connexion')
    expect(screen.queryByLabelText('Ouvrir le menu du compte')).toBeNull()
  })

  it('connecté : cloche avec badge et menu du compte en bottom sheet', async () => {
    mockedAuth.mockReturnValue({
      user: { id: 7, username: 'ada', role: 'USER' },
      isAuthenticated: true,
      logout: vi.fn(),
    } as unknown as AuthValue)
    renderHeader()
    expect(screen.getByLabelText(/Notifications/)).toBeVisible()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu du compte'))
    expect(await screen.findByRole('dialog')).toBeVisible()
    expect(screen.getByText('Mon profil')).toBeVisible()
    expect(screen.getByText('Portefeuille')).toBeVisible()
    expect(screen.getByText('Déconnexion')).toBeVisible()
  })

  it('créateur : le menu expose le Creator Studio', async () => {
    mockedAuth.mockReturnValue({
      user: { id: 9, username: 'star', role: 'CREATOR' },
      isAuthenticated: true,
      logout: vi.fn(),
    } as unknown as AuthValue)
    renderHeader()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu du compte'))
    expect(await screen.findByText('Creator Studio')).toBeVisible()
  })
})
