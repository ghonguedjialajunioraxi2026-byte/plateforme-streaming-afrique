/// Fuseaux horaires (étape 19) — le backend stocke des `Instant` UTC ;
/// l'affichage convertit dans le fuseau de l'utilisateur (profil >
/// navigateur). `Intl` natif : aucune dépendance.

/** Fuseau effectif : préférence profil, sinon navigateur. */
export function effectiveTimezone(profileTimezone?: string | null): string {
  if (profileTimezone) {
    try {
      Intl.DateTimeFormat(undefined, { timeZone: profileTimezone })
      return profileTimezone
    } catch {
      // Fuseau invalide : repli navigateur.
    }
  }
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return 'UTC'
  }
}

/** `2026-…Z` → « 12/02 à 20:00 (Africa/Douala) » dans le fuseau donné. */
export function formatLocalDateTime(
  iso: string,
  timezone: string,
  locale = 'fr-FR',
): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  try {
    const formatted = new Intl.DateTimeFormat(locale, {
      day: '2-digit',
      month: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      timeZone: timezone,
    }).format(date)
    return `${formatted} (${timezone})`
  } catch {
    return date.toISOString()
  }
}
