import { describe, expect, it } from 'vitest'
import {
  passwordStrength,
  validateEmail,
  validatePassword,
  validateUsername,
} from './validators'

describe('validateUsername', () => {
  it('accepte un pseudo valide', () => {
    expect(validateUsername('user237')).toBeNull()
    expect(validateUsername('ada.lovelace_92')).toBeNull()
  })

  it('refuse vide, trop court, trop long, caractères interdits', () => {
    expect(validateUsername('')).not.toBeNull()
    expect(validateUsername('ab')).not.toBeNull()
    expect(validateUsername('x'.repeat(31))).not.toBeNull()
    expect(validateUsername('mauvais pseudo!')).not.toBeNull()
    expect(validateUsername('accent-é')).not.toBeNull()
  })
})

describe('validateEmail', () => {
  it('accepte un email valide', () => {
    expect(validateEmail('user@example.com')).toBeNull()
  })

  it('refuse vide, mal formé, trop long', () => {
    expect(validateEmail('')).not.toBeNull()
    expect(validateEmail('pas-un-email')).not.toBeNull()
    expect(validateEmail(`${'a'.repeat(140)}@example.com`)).not.toBeNull()
  })
})

describe('validatePassword', () => {
  it('accepte un mot de passe fort', () => {
    expect(validatePassword('MotDePasseFort123!')).toBeNull()
  })

  it('refuse court, sans chiffre, sans lettre', () => {
    expect(validatePassword('Court1!')).not.toBeNull()
    expect(validatePassword('motdepasse')).not.toBeNull()
    expect(validatePassword('12345678')).not.toBeNull()
  })
})

describe('passwordStrength', () => {
  it('score 0 à vide, 4 au maximum', () => {
    expect(passwordStrength('')).toBe(0)
    expect(passwordStrength('MotDePasseFort123!')).toBe(4)
  })

  it('monte avec la longueur et la diversité', () => {
    expect(passwordStrength('motdepasse')).toBeGreaterThanOrEqual(1)
    expect(passwordStrength('Motdepasse1')).toBeGreaterThan(passwordStrength('motdepasse'))
  })
})
