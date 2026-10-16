import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import CreatorSettings from './CreatorSettings'
import { creatorsApi } from '../../services/api/creators'
import { categoriesApi } from '../../services/api/categories'
import { AuthProvider } from '../../features/auth/AuthContext'
import { ToastProvider } from '../../components/ui'
import { mockAuthenticatedFetch } from '../../test/mockAuth'

vi.mock('../../services/api/creators', () => ({
  creatorsApi: {
    getMyChannel: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    uploadAvatar: vi.fn(),
    uploadBanner: vi.fn(),
  },
}))

vi.mock('../../services/api/categories', () => ({
  categoriesApi: {
    list: vi.fn(),
  },
}))

const CHANNEL = {
  id: 5,
  displayName: 'Star TV',
  slug: 'star-tv',
  bio: 'Directs',
  avatarUrl: null,
  bannerUrl: null,
  userId: 7,
  createdAt: '2026-01-01T00:00:00Z',
  followerCount: 10,
  primaryCategory: null,
  status: 'ACTIVE' as const,
}

function renderPage() {
  render(
    <MemoryRouter>
      <ToastProvider>
        <AuthProvider>
          <CreatorSettings />
        </AuthProvider>
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('CreatorSettings', () => {
  beforeEach(() => {
    mockAuthenticatedFetch()
  })

  afterEach(() => {
    vi.clearAllMocks()
    vi.unstubAllGlobals()
  })

  it('sans chaîne → création (nom + catégorie)', async () => {
    vi.mocked(categoriesApi.list).mockResolvedValue({
      content: [{ id: 1, name: 'Jeux', slug: 'jeux', description: null }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(creatorsApi.getMyChannel).mockRejectedValue(new Error('NOT_FOUND'))
    vi.mocked(creatorsApi.create).mockImplementation(async (_userId, data) => ({
      ...CHANNEL,
      displayName: data.displayName,
    }))

    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Créer ma chaîne' })).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText(/Nom de la chaîne/), {
      target: { value: 'Nouvelle TV' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Créer ma chaîne' }))

    await waitFor(() => {
      expect(creatorsApi.create).toHaveBeenCalledWith(
        expect.any(Number),
        expect.objectContaining({ displayName: 'Nouvelle TV' }),
      )
    })
  })

  it('avec chaîne → formulaire pré-rempli + mise à jour', async () => {
    vi.mocked(categoriesApi.list).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(creatorsApi.getMyChannel).mockResolvedValue({ ...CHANNEL })
    vi.mocked(creatorsApi.update).mockImplementation(async (_id, data) => ({
      ...CHANNEL,
      displayName: data.displayName,
    }))

    renderPage()
    await waitFor(() => {
      expect(screen.getByDisplayValue('Star TV')).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText(/Nom de la chaîne/), {
      target: { value: 'Star Renommée' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => {
      expect(creatorsApi.update).toHaveBeenCalledWith(
        5,
        expect.objectContaining({ displayName: 'Star Renommée' }),
      )
    })
  })

  it('nom vide → erreur locale, aucun appel', async () => {
    vi.mocked(categoriesApi.list).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(creatorsApi.getMyChannel).mockRejectedValue(new Error('NOT_FOUND'))

    renderPage()
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: 'Créer ma chaîne' })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: 'Créer ma chaîne' }))

    await waitFor(() => {
      expect(screen.getByText('Le nom de chaîne est obligatoire')).toBeInTheDocument()
    })
    expect(creatorsApi.create).not.toHaveBeenCalled()
  })
})
