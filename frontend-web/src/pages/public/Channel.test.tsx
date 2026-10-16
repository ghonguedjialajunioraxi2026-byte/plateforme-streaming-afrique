import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import Channel from './Channel'
import { creatorsApi } from '../../services/api/creators'
import { streamsApi } from '../../services/api/streams'
import { ApiError } from '../../services/http'

vi.mock('../../services/api/creators', () => ({
  creatorsApi: {
    getBySlug: vi.fn(),
  },
}))

vi.mock('../../services/api/streams', () => ({
  streamsApi: {
    byCreator: vi.fn(),
  },
}))

vi.mock('../../services/api/videos', () => ({
  videosApi: {
    list: vi.fn(),
  },
}))

vi.mock('../../services/api/clips', () => ({
  clipsApi: {
    list: vi.fn(),
  },
}))

// Visiteur non connecté : FollowButton dégrade vers le suivi local.
vi.mock('../../features/auth/useAuth', () => ({
  useAuth: () => ({ user: null, isAuthenticated: false, logout: vi.fn() }),
}))

vi.mock('../../components/ui/toast-context', () => ({
  useToast: () => ({ show: vi.fn() }),
}))

const DETAIL = {
  id: 3,
  displayName: 'Star TV',
  slug: 'star-tv',
  bio: 'Directs chaque soir',
  avatarUrl: '/uploads/avatars/uuid.png',
  bannerUrl: '/uploads/banners/uuid.png',
  userId: 9,
  createdAt: '2026-01-01T00:00:00Z',
  followerCount: 1200,
  primaryCategory: { id: 1, slug: 'jeux', name: 'Jeux' },
  status: 'ACTIVE' as const,
}

const EMPTY_PAGE = { content: [], page: 0, size: 1, totalElements: 0, totalPages: 0 }

function renderAt(slug: string) {
  render(
    <MemoryRouter initialEntries={[`/channel/${slug}`]}>
      <Routes>
        <Route path="/channel/:slug" element={<Channel />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Channel', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('affiche avatar, bannière, nom, catégorie, followers', async () => {
    vi.mocked(creatorsApi.getBySlug).mockResolvedValue({ ...DETAIL })
    vi.mocked(streamsApi.byCreator).mockResolvedValue({ ...EMPTY_PAGE })
    renderAt('star-tv')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Star TV/ })).toBeInTheDocument()
    })
    expect(screen.getByAltText('Bannière de Star TV')).toBeInTheDocument()
    expect(screen.getByText(/@star-tv/)).toBeInTheDocument()
    expect(screen.getByText(/1,2k abonné/)).toBeInTheDocument()
    expect(screen.getByText('Catégorie : Jeux')).toBeInTheDocument()
    expect(creatorsApi.getBySlug).toHaveBeenCalledWith('star-tv')
  })

  it('chaîne inexistante → 404 explicite', async () => {
    vi.mocked(creatorsApi.getBySlug).mockRejectedValue(
      new ApiError(404, 'Chaîne introuvable : @fantome', 'NOT_FOUND'),
    )
    renderAt('fantome')

    await waitFor(() => {
      expect(screen.getByText('404 — Chaîne introuvable')).toBeInTheDocument()
    })
  })

  it('live en cours → bannière cliquable vers /live/:id', async () => {
    vi.mocked(creatorsApi.getBySlug).mockResolvedValue({ ...DETAIL, bannerUrl: null })
    vi.mocked(streamsApi.byCreator).mockResolvedValue({
      ...EMPTY_PAGE,
      content: [
        {
          id: 42,
          title: 'Soirée jeux',
          description: null,
          creatorId: 3,
          creatorSlug: 'star-tv',
          categoryId: 1,
          categorySlug: 'jeux',
          categoryName: 'Jeux',
          status: 'LIVE',
          thumbnailUrl: null,
          language: 'fr',
          visibility: 'PUBLIC',
          playbackUrl: null,
          viewerCount: 150,
          peakViewers: 200,
          scheduledStartAt: null,
          startedAt: '2026-01-01T20:00:00Z',
          endedAt: null,
          createdAt: '2026-01-01T19:00:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
    })
    renderAt('star-tv')

    await waitFor(() => {
      expect(screen.getByText('Soirée jeux')).toBeInTheDocument()
    })
    expect(screen.getByRole('link', { name: /Soirée jeux/ })).toHaveAttribute(
      'href',
      '/live/42',
    )
  })

  it('hors ligne → aucune bannière live, onglets VOD/clips intacts', async () => {
    vi.mocked(creatorsApi.getBySlug).mockResolvedValue({ ...DETAIL, bannerUrl: null })
    vi.mocked(streamsApi.byCreator).mockResolvedValue({ ...EMPTY_PAGE })
    renderAt('star-tv')

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /Star TV/ })).toBeInTheDocument()
    })
    expect(screen.queryByText(/en ce moment/)).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Vidéos' })).toBeInTheDocument()
  })
})
