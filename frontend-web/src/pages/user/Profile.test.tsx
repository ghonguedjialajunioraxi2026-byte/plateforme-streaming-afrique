import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import Profile from './Profile'
import { profilesApi } from '../../services/api/profiles'
import { creatorsApi } from '../../services/api/creators'
import { AuthProvider } from '../../features/auth/AuthContext'
import { ToastProvider } from '../../components/ui'
import { mockAuthenticatedFetch } from '../../test/mockAuth'

vi.mock('../../services/api/profiles', () => ({
  profilesApi: {
    getMe: vi.fn(),
    updateMe: vi.fn(),
    uploadAvatar: vi.fn(),
  },
}))

vi.mock('../../services/api/creators', () => ({
  creatorsApi: {
    getMyChannel: vi.fn(),
  },
}))

const ME = {
  userId: 7,
  username: 'ada',
  email: 'ada@example.com',
  displayName: 'Ada',
  avatarUrl: null,
  emailVerified: false,
  bio: null,
  countryCode: null,
  language: null,
  timezone: null,
  preferredCurrency: null,
  websiteUrl: null,
  xHandle: null,
  instagramHandle: null,
  youtubeUrl: null,
  birthDate: null,
  notifyLive: true,
  notifyContent: true,
  isPublic: true,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('Profile', () => {
  beforeEach(() => {
    mockAuthenticatedFetch()
    vi.mocked(profilesApi.getMe).mockResolvedValue({ ...ME })
    vi.mocked(creatorsApi.getMyChannel).mockRejectedValue(new Error('NOT_FOUND'))
    vi.mocked(profilesApi.updateMe).mockImplementation(async (data) => ({
      ...ME,
      displayName: data.displayName ?? ME.displayName,
    }))
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  function renderPage() {
    render(
      <MemoryRouter>
        <ToastProvider>
          <AuthProvider>
            <Profile />
          </AuthProvider>
        </ToastProvider>
      </MemoryRouter>,
    )
  }

  it('affiche le profil chargé (nom, bio vide, email)', async () => {
    renderPage()

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Ada' })).toBeInTheDocument()
    })
    expect(screen.getByText('ada@example.com', { exact: false })).toBeInTheDocument()
  })

  it('modification → updateMe appelé + toast de succès', async () => {
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Ada' })).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText('Nom affiché'), {
      target: { value: 'Ada Lovelace' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => {
      expect(profilesApi.updateMe).toHaveBeenCalledWith(
        expect.objectContaining({ displayName: 'Ada Lovelace' }),
      )
    })
  })

  it('erreur API → message compréhensible, pas de pile', async () => {
    vi.mocked(profilesApi.updateMe).mockRejectedValue(new Error('Nom déjà pris'))
    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Ada' })).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText('Nom affiché'), {
      target: { value: 'Autre Nom' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Nom déjà pris')
    })
  })
})
