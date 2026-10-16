import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { BottomSheet } from './BottomSheet'

describe('BottomSheet', () => {
  it('ne rend rien quand fermé', () => {
    const onClose = vi.fn()
    render(
      <BottomSheet open={false} onClose={onClose} title="Filtres">
        <p>contenu</p>
      </BottomSheet>,
    )
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('affiche le titre et le contenu quand ouvert', () => {
    render(
      <BottomSheet open onClose={vi.fn()} title="Filtres">
        <p>contenu du panneau</p>
      </BottomSheet>,
    )
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.getByText('Filtres')).toBeVisible()
    expect(screen.getByText('contenu du panneau')).toBeVisible()
  })

  it('ferme avec Escape', () => {
    const onClose = vi.fn()
    render(
      <BottomSheet open onClose={onClose}>
        <p>contenu</p>
      </BottomSheet>,
    )
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it("ferme au clic sur l'overlay", () => {
    const onClose = vi.fn()
    render(
      <BottomSheet open onClose={onClose}>
        <p>contenu</p>
      </BottomSheet>,
    )
    fireEvent.click(screen.getByLabelText('Fermer le panneau'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })
})
