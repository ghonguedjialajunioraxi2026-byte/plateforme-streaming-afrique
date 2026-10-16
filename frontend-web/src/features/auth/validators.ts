/// Validation des formulaires auth (étape 08) — miroir des règles backend
/// (`RegisterRequest`, `PasswordPolicy`) pour un retour immédiat, SANS
/// remplacer la validation serveur (seule faisant foi).
///
/// Retour : message d'erreur en français, ou `null` si valide.

const USERNAME_PATTERN = /^[a-zA-Z0-9._-]+$/
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

export function validateUsername(value: string): string | null {
  const username = value.trim()
  if (username.length === 0) {
    return "Le nom d'utilisateur est obligatoire"
  }
  if (username.length < 3 || username.length > 30) {
    return "Le nom d'utilisateur doit contenir entre 3 et 30 caractères"
  }
  if (!USERNAME_PATTERN.test(username)) {
    return 'Lettres, chiffres, points, tirets et underscores uniquement'
  }
  return null
}

export function validateEmail(value: string): string | null {
  const email = value.trim()
  if (email.length === 0) {
    return "L'email est obligatoire"
  }
  if (email.length > 150) {
    return "L'email ne doit pas dépasser 150 caractères"
  }
  if (!EMAIL_PATTERN.test(email)) {
    return "L'email doit être valide"
  }
  return null
}

export function validatePassword(value: string): string | null {
  if (value.length === 0) {
    return 'Le mot de passe est obligatoire'
  }
  if (value.length < 8 || value.length > 100) {
    return 'Le mot de passe doit contenir entre 8 et 100 caractères'
  }
  if (!/[A-Za-z]/.test(value) || !/[0-9]/.test(value)) {
    return 'Le mot de passe doit contenir au moins une lettre et un chiffre'
  }
  return null
}

/** Score 0-4 pour l'indicateur de force (inscription) : longueur + diversité. */
export function passwordStrength(value: string): number {
  if (value.length === 0) {
    return 0
  }
  let score = 0
  if (value.length >= 8) {
    score += 1
  }
  if (value.length >= 12) {
    score += 1
  }
  if (/[A-Z]/.test(value) && /[a-z]/.test(value)) {
    score += 1
  }
  if (/[0-9]/.test(value) && /[^A-Za-z0-9]/.test(value)) {
    score += 1
  }
  return score
}
