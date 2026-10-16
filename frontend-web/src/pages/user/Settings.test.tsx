import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import Settings from './Settings'
import { authApi } from '../../services/api/auth'
import { AuthProvider } from '../../features/auth/AuthContext'
import { ThemeProvider } from '../../features/theme/ThemeContext'
import { ToastProvider } from '../../components/ui'
import { ApiError } from '../../services/http'

vi.mock('../../services/api/auth', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../services/api/auth')>()
  return {
    ...original,
    authApi: {
      ...original.authApi,
      changePassword: vi.fn(),
    },
  }
})

function renderPage() {
  render(
    <MemoryRouter>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            <Settings />
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

describe('Settings', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('changement valide → appel + confirmation', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue(null)
    renderPage()

    fireEvent.change(screen.getByLabelText('Mot de passe actuel'), {
      target: { value: 'Ancien123!' },
    })
    fireEvent.change(screen.getByLabelText('Nouveau mot de passe (min 8, lettre + chiffre)'), {
      target: { value: 'NouveauFort456!' },
    })
    fireEvent.change(screen.getByLabelText('Confirmation'), {
      target: { value: 'NouveauFort456!' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))

    await waitFor(() => {
      expect(authApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'Ancien123!',
        newPassword: 'NouveauFort456!',
        confirmPassword: 'NouveauFort456!',
      })
    })
  })

  it('confirmation différente → erreur locale, aucun appel', async () => {
    renderPage()

    fireEvent.change(screen.getByLabelText('Mot de passe actuel'), {
      target: { value: 'Ancien123!' },
    })
    fireEvent.change(screen.getByLabelText('Nouveau mot de passe (min 8, lettre + chiffre)'), {
      target: { value: 'NouveauFort456!' },
    })
    fireEvent.change(screen.getByLabelText('Confirmation'), {
      target: { value: 'Autre7890!' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))

    await waitFor(() => {
      expect(screen.getByText('La confirmation diffère du mot de passe')).toBeInTheDocument()
    })
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('erreur serveur → message affiché, pas de pile', async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue(
      new ApiError(400, 'Le mot de passe actuel est incorrect', 'BAD_REQUEST'),
    )
    renderPage()

    fireEvent.change(screen.getByLabelText('Mot de passe actuel'), {
      target: { value: 'Faux1234!' },
    })
    fireEvent.change(screen.getByLabelText('Nouveau mot de passe (min 8, lettre + chiffre)'), {
      target: { value: 'NouveauFort456!' },
    })
    fireEvent.change(screen.getByLabelText('Confirmation'), {
      target: { value: 'NouveauFort456!' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Changer le mot de passe' }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('Le mot de passe actuel est incorrect')
    })
  })
})
