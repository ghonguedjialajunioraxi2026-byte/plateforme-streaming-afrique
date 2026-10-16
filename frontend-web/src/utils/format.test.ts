import { describe, expect, it } from 'vitest'
import { formatCount, formatDate, formatDuration } from './format'

describe('formatDuration', () => {
  it('secondes → m:ss', () => {
    expect(formatDuration(25)).toBe('0:25')
    expect(formatDuration(125)).toBe('2:05')
    expect(formatDuration(0)).toBe('0:00')
  })

  it('heures → h:mm:ss', () => {
    expect(formatDuration(3725)).toBe('1:02:05')
    expect(formatDuration(3600)).toBe('1:00:00')
  })

  it('valeurs limites clampées', () => {
    expect(formatDuration(-5)).toBe('0:00')
    expect(formatDuration(61.9)).toBe('1:01')
  })
})

describe('formatCount', () => {
  it('compacte FR', () => {
    expect(formatCount(999)).toBe('999')
    expect(formatCount(12_500)).toBe('12,5k')
    expect(formatCount(1_200_000)).toBe('1,2M')
  })
})

describe('formatDate', () => {
  it('ISO → jj/mm/aaaa', () => {
    expect(formatDate('2026-09-14T10:00:00Z')).toBe('14/09/2026')
    expect(formatDate('invalide')).toBe('invalide')
  })
})
