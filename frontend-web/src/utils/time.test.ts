import { describe, expect, it } from 'vitest'
import { effectiveTimezone, formatLocalDateTime } from './time'

describe('time (fuseaux)', () => {
  it('préférence profil valide prioritaire', () => {
    expect(effectiveTimezone('Africa/Douala')).toBe('Africa/Douala')
  })

  it('fuseau invalide → repli navigateur', () => {
    const fallback = effectiveTimezone('Mars/Olympus')
    expect(typeof fallback).toBe('string')
    expect(fallback.length).toBeGreaterThan(0)
  })

  it('format local avec fuseau explicite', () => {
    const text = formatLocalDateTime('2026-02-12T19:00:00Z', 'Africa/Douala', 'fr-FR')
    expect(text).toContain('Africa/Douala')
    // 19:00 UTC = 20:00 à Douala (UTC+1).
    expect(text).toContain('20:00')
  })

  it('entrée invalide → écho sans crash', () => {
    expect(formatLocalDateTime('pas-une-date', 'Africa/Douala')).toBe('pas-une-date')
  })
})
