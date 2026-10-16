/// Service d'authentification — contrat RÉEL du backend Spring Boot (étape 08).
/// Enveloppes déjà déballées par `http` : on reçoit directement `data`.
import { http } from '../http'
import type { Role } from '../../types/domain'

export interface AuthUser {
  id: number
  username: string
  email: string
  displayName: string
  role: Role
  emailVerified: boolean
}

export interface TokenPair {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
}

export interface LoginResponse extends TokenPair {
  userId: number
  email: string
  displayName: string
  role: Role
}

export interface AuthSession {
  id: number
  deviceInfo: string | null
  ipAddress: string | null
  createdAt: string
  lastUsedAt: string
  expiresAt: string
}

export const authApi = {
  register: (data: { username: string; email: string; password: string; confirmPassword: string; inviteCode?: string }) =>
    http.post<AuthUser>('/auth/register', data),

  login: (data: { email: string; password: string }) =>
    http.post<LoginResponse>('/auth/login', data),

  /**
   * Connexion Google (flux ID token OIDC) : transmet UNIQUEMENT l'id_token
   * brut remis par Google Identity Services — jamais un email seul (qui ne
   * prouverait rien). Le backend vérifie le token (signature, émetteur,
   * audience, expiration) puis remet la paire JWT FLICK habituelle.
   * 409 = email déjà pris par un compte LOCAL (aucun doublon créé).
   */
  google: (idToken: string) =>
    http.post<LoginResponse>('/auth/google', { idToken }),

  /**
   * Liaison Google (connecté) : lie le compte Google au compte courant.
   * Requiert le Bearer (c'est la session FLICK qui prouve l'identité).
   */
  linkGoogle: (idToken: string) =>
    http.post<AuthUser>('/auth/google/link', { idToken }),

  refresh: (refreshToken: string) =>
    http.post<TokenPair>('/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    http.post<null>('/auth/logout', { refreshToken }),

  me: () => http.get<AuthUser>('/auth/me'),

  verifyEmail: (token: string) =>
    http.post<null>('/auth/verify-email', { token }),

  resendVerification: (email: string) =>
    http.post<null>('/auth/resend-verification', { email }),

  forgotPassword: (email: string) =>
    http.post<null>('/auth/forgot-password', { email }),

  resetPassword: (data: { token: string; newPassword: string; confirmPassword: string }) =>
    http.post<null>('/auth/reset-password', data),

  changePassword: (data: { currentPassword: string; newPassword: string; confirmPassword: string }) =>
    http.post<null>('/auth/change-password', data),

  sessions: () => http.get<AuthSession[]>('/auth/sessions'),

  revokeSession: (id: number) => http.delete<null>(`/auth/sessions/${id}`),
}
