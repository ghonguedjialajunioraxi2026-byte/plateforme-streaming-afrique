import { describe, expect, it, beforeEach } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { ThemeProvider } from './ThemeContext'
import { ThemeToggle } from './ThemeToggle'

const STORAGE_KEY = 'flick-theme'

beforeEach(() => {
  window.localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})

describe('ThemeToggle (sombre / clair)', () => {
  it('affiche le mode sombre par défaut avec le libellé vers le clair', () => {
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    )
    const button = screen.getByRole('button', { name: 'Passer en mode clair' })
    expect(button).toBeInTheDocument()
    expect(document.documentElement.dataset.theme).toBe('dark')
  })

  it('bascule sombre → clair → sombre, persiste et met à jour le DOM', async () => {
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    )

    const toLight = screen.getByRole('button', { name: 'Passer en mode clair' })
    fireEvent.click(toLight)

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('light')
    expect(screen.getByRole('button', { name: 'Passer en mode sombre' })).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Passer en mode sombre' }))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('dark')
  })

  it('restaure le thème clair stocké sans flash (anti-FOUC)', () => {
    window.localStorage.setItem(STORAGE_KEY, 'light')
    render(
      <ThemeProvider>
        <ThemeToggle />
      </ThemeProvider>,
    )
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(screen.getByRole('button', { name: 'Passer en mode sombre' })).toBeInTheDocument()
  })
})
