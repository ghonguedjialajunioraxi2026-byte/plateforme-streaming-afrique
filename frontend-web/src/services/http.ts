/// Client HTTP de base — utilisé par tous les services API.
///
/// Enveloppe backend : succès `{success:true, message, data, timestamp}`,
/// erreur `{success:false, status, errorCode, message, errors?, path, timestamp}`.
/// Le client déballe `data` et lève `ApiError` (jamais de pile, jamais de secret).
///
/// Authentification (§27-28, analyse documentée dans `features/auth/AuthContext`) :
/// - access token (15 min) : UNIQUEMENT en mémoire (variable module, jamais
///   persisté) → présenté en `Authorization: Bearer` ;
/// - refresh token (7 j) : `localStorage` (clé `sa_refresh_token`) — persistance
///   entre rechargements. Les cookies HttpOnly ont été écartés pour l'instant :
///   en dev le frontend (5173) et l'API (8080) sont cross-origin, un cookie
///   `SameSite=Lax` n'est pas renvoyé sur les POST fetch cross-site et
///   `SameSite=None` exigerait HTTPS + protection CSRF dédiée. Le refresh
///   étant révocable côté serveur (rotation + logout), le vol reste contenu.
/// - 401 → UN SEUL refresh en vol (single-flight : les requêtes concurrentes
///   attendent la même promesse, pas de boucle), retry unique, puis logout.
///   Jamais de refresh sur les routes /auth/login et /auth/refresh elles-mêmes.

const API_BASE_URL =
  import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api'

const REFRESH_STORAGE_KEY = 'sa_refresh_token'

export class ApiError extends Error {
  status: number
  errorCode?: string
  errors?: Record<string, string>

  constructor(status: number, message: string, errorCode?: string, errors?: Record<string, string>) {
    super(message)
    this.status = status
    this.errorCode = errorCode
    this.errors = errors
  }
}

/** Expiration de session : le store déconnecte et redirige vers /connexion. */
export type AuthExpiredHandler = () => void

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null
let onAuthExpired: AuthExpiredHandler | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setRefreshToken(token: string | null): void {
  try {
    if (token === null) {
      localStorage.removeItem(REFRESH_STORAGE_KEY)
    } else {
      localStorage.setItem(REFRESH_STORAGE_KEY, token)
    }
  } catch {
    // Stockage indisponible (navigation privée) : session mémoire uniquement.
  }
}

export function setAuthExpiredHandler(handler: AuthExpiredHandler | null): void {
  onAuthExpired = handler
}

interface Envelope<T> {
  success: boolean
  message: string | null
  data: T
}

async function parseError(response: Response): Promise<ApiError> {
  let message = `API error: ${response.status}`
  let errorCode: string | undefined
  let errors: Record<string, string> | undefined
  try {
    const body = (await response.json()) as {
      message?: string
      errorCode?: string
      errors?: Record<string, string>
    }
    if (typeof body.message === 'string' && body.message.length > 0) {
      message = body.message
    }
    errorCode = body.errorCode
    errors = body.errors
  } catch {
    // Corps illisible : on garde le message générique (jamais de pile).
  }
  return new ApiError(response.status, message, errorCode, errors)
}

/** Refresh single-flight : une seule requête réseau même en cas d'appels concurrents. */
async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise !== null) {
    return refreshPromise
  }
  refreshPromise = (async () => {
    const refreshToken = getRefreshToken()
    if (refreshToken === null) {
      return null
    }
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!response.ok) {
      return null
    }
    const body = (await response.json()) as Envelope<{
      accessToken: string
      refreshToken: string
    }>
    setAccessToken(body.data.accessToken)
    setRefreshToken(body.data.refreshToken)
    return body.data.accessToken
  })()
  try {
    return await refreshPromise
  } finally {
    refreshPromise = null
  }
}

function isAuthRoute(path: string): boolean {
  return path === '/auth/login' || path === '/auth/refresh'
}

async function request<T>(path: string, options?: RequestInit, retried = false): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (accessToken !== null) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })

  if (response.status === 401 && !retried && !isAuthRoute(path)) {
    const renewed = await refreshAccessToken()
    if (renewed !== null) {
      return request<T>(path, options, true)
    }
    setAccessToken(null)
    setRefreshToken(null)
    onAuthExpired?.()
  }

  if (!response.ok) {
    throw await parseError(response)
  }
  const body = (await response.json()) as Envelope<T>
  return body.data
}

/**
 * Envoi multipart (upload d'images) — PAS de Content-Type forcé : le
 * navigateur génère le boundary. Réutilise l'intercepteur refresh.
 */
async function postForm<T>(path: string, formData: FormData): Promise<T> {
  const headers: Record<string, string> = {}
  if (accessToken !== null) {
    headers['Authorization'] = `Bearer ${accessToken}`
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers,
    body: formData,
  })

  if (response.status === 401 && !isAuthRoute(path)) {
    const renewed = await refreshAccessToken()
    if (renewed !== null) {
      return postForm<T>(path, formData)
    }
    setAccessToken(null)
    setRefreshToken(null)
    onAuthExpired?.()
  }

  if (!response.ok) {
    throw await parseError(response)
  }
  const body = (await response.json()) as Envelope<T>
  return body.data
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  postForm,
}
