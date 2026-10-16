/// Types du domaine auth (étape 08) — réexportés depuis le contrat API
/// (source unique : `services/api/auth.ts`, pas de duplication).
export type {
  AuthUser,
  TokenPair,
  LoginResponse,
  AuthSession,
} from '../../services/api/auth'

/// Hiérarchie des rôles (miroir backend : USER < CREATOR < MODERATOR < ADMIN).
/// `SUPER_ADMIN` n'existe QUE côté frontend (routes historiques) : aucun
/// compte backend ne le porte, il ne donne aucun droit supplémentaire.
const ROLE_RANK: Record<string, number> = {
  USER: 1,
  CREATOR: 2,
  MODERATOR: 3,
  ADMIN: 4,
  SUPER_ADMIN: 5,
}

/** Le rôle `actual` satisfait-il l'exigence `required` (hiérarchie) ? */
export function hasRoleAtLeast(actual: string | undefined, required: string): boolean {
  if (actual === undefined) {
    return false
  }
  return (ROLE_RANK[actual] ?? 0) >= (ROLE_RANK[required] ?? Number.MAX_SAFE_INTEGER)
}
