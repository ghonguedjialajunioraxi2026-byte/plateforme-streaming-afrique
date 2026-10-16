import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VideoPlayer } from './VideoPlayer'
import { I18nProvider } from '../../i18n'
import { DataSaverProvider } from '../../features/region/DataSaverContext'

/// jsdom = `navigator.language` en-US → interface anglaise (résolution
/// navigateur vérifiée ici même : aucune langue stockée, pas de profil).
function renderPlayer(props: { src?: string } = {}) {
  return render(
    <I18nProvider>
      <DataSaverProvider>
        <VideoPlayer {...props} />
      </DataSaverProvider>
    </I18nProvider>,
  )
}

describe('VideoPlayer (data saver + résilience)', () => {
  it('sans source → état d’attente, pas d’erreur', () => {
    renderPlayer()
    expect(screen.getByText('Stream signal coming soon')).toBeDefined()
  })

  it('avec source → menu qualité, pas de crash', () => {
    renderPlayer({ src: 'https://cdn.example.com/a.mp4' })
    expect(screen.getByTitle('Quality')).toBeDefined()
    expect(screen.getByText('Auto')).toBeDefined()
  })

  it('qualités préparées listées (144p → 1080p)', () => {
    renderPlayer({ src: 'https://cdn.example.com/a.mp4' })
    const select = screen.getByTitle('Quality')
    for (const quality of ['144p', '240p', '360p', '480p', '720p', '1080p']) {
      expect(select.textContent).toContain(quality)
    }
  })
})
