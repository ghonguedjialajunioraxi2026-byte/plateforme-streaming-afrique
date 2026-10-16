import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import CreatorAI from './CreatorAI'
import { aiApi } from '../../services/api/ai'
import { ToastProvider } from '../../components/ui'

vi.mock('../../services/api/ai', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../services/api/ai')>()
  return {
    ...original,
    aiApi: {
      ...original.aiApi,
      titles: vi.fn(),
      description: vi.fn(),
      tags: vi.fn(),
      vodSummary: vi.fn(),
      clipSuggestions: vi.fn(),
      analytics: vi.fn(),
    },
  }
})

function renderPage() {
  render(
    <MemoryRouter>
      <ToastProvider>
        <CreatorAI />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('CreatorAI', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('affiche les 5 onglets', () => {
    renderPage()
    expect(screen.getByRole('tab', { name: 'Titres' })).toBeDefined()
    expect(screen.getByRole('tab', { name: 'Analytics' })).toBeDefined()
  })

  it('titres → suggestions affichées', async () => {
    vi.mocked(aiApi.titles).mockResolvedValue({
      suggestions: ['Titre A', 'Titre B', 'Titre C'],
      provider: 'stub',
      model: 'flick-stub-1',
    })
    renderPage()

    fireEvent.change(screen.getByLabelText('Sujet du live'), {
      target: { value: 'match de foot' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Suggérer 3 titres' }))

    await waitFor(() => {
      expect(screen.getByText('Titre A')).toBeDefined()
    })
    expect(aiApi.titles).toHaveBeenCalledOnce()
  })

  it('erreur quota → toast sans crash', async () => {
    const { ApiError } = await import('../../services/http')
    vi.mocked(aiApi.titles).mockRejectedValue(
      new ApiError(429, 'Quota IA quotidien atteint', 'AI_QUOTA_EXCEEDED'),
    )
    renderPage()

    fireEvent.change(screen.getByLabelText('Sujet du live'), {
      target: { value: 'gaming' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Suggérer 3 titres' }))

    await waitFor(() => {
      expect(screen.getByText('Quota IA quotidien atteint')).toBeDefined()
    })
  })
})
