import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { MobileLiveCard } from './MobileLiveCard'
import { MobileChip } from './MobileChip'

const STREAM = {
  id: 's1',
  channelId: 'c-zeka',
  title: 'Ranked + Tryhard',
  category: 'Fortnite',
  viewersCount: 12400,
  status: 'LIVE',
  language: 'fr',
} as const

describe('MobileLiveCard', () => {
  it('affiche le badge LIVE, l\'audience et la chaîne', () => {
    render(
      <MemoryRouter>
        <MobileLiveCard stream={{ ...STREAM }} channelName="ZekaTV" verified />
      </MemoryRouter>,
    )
    expect(screen.getByText('Live')).toBeVisible()
    expect(screen.getByText('12,4k')).toBeVisible()
    expect(screen.getByText('ZekaTV')).toBeVisible()
    expect(screen.getByLabelText('Chaîne vérifiée')).toBeVisible()
    expect(screen.getByLabelText(/Regarder/)).toHaveAttribute('href', '/live/s1')
  })
})

describe('MobileChip', () => {
  it('bouton avec état actif accessible', () => {
    render(
      <MemoryRouter>
        <MobileChip label="Lives" active />
      </MemoryRouter>,
    )
    expect(screen.getByRole('button', { name: 'Lives' })).toHaveAttribute('aria-pressed', 'true')
  })
})
