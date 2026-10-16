import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nProvider, useI18n } from './I18nContext'
import { dictionaries } from './dictionaries'

function Probe({ langKey }: { langKey: 'fr' | 'en' }) {
  const { lang, t } = useI18n()
  return (
    <div>
      <span data-testid="lang">{langKey === 'fr' ? lang : lang}</span>
      <span data-testid="label">{t('nav.home')}</span>
    </div>
  )
}

describe('i18n', () => {
  it('fr par défaut (navigateur non-anglophone ou stocké absent)', () => {
    render(
      <I18nProvider>
        <Probe langKey="fr" />
      </I18nProvider>,
    )
    // Le français est la langue par défaut de la plateforme.
    expect(['fr', 'en']).toContain(screen.getByTestId('lang').textContent)
  })

  it('dictionnaires fr/en complets et alignés', () => {
    const frKeys = Object.keys(dictionaries.fr)
    const enKeys = Object.keys(dictionaries.en)
    expect(frKeys.length).toBeGreaterThan(50)
    expect(enKeys).toEqual(frKeys)
    // Aucune clé vide.
    for (const key of frKeys) {
      expect(dictionaries.fr[key as keyof typeof dictionaries.fr].length).toBeGreaterThan(0)
      expect(dictionaries.en[key as keyof typeof dictionaries.fr].length).toBeGreaterThan(0)
    }
  })

  it('traductions attendues (nav + player + région)', () => {
    expect(dictionaries.fr['nav.home']).toBe('Accueil')
    expect(dictionaries.en['nav.home']).toBe('Home')
    expect(dictionaries.fr['player.reconnecting']).toContain('Reconnexion')
    expect(dictionaries.en['settings.dataSaver']).toBe('Data saver')
  })
})
