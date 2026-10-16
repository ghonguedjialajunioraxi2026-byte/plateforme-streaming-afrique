/// Bouton officiel "Continuer avec Google" (Google Identity Services).
///
/// Le bouton est rendu PAR Google (charte imposée, anti-hameçonnage) dans un
/// conteneur pleine largeur. Au clic, Google authentifie l'utilisateur puis
/// rappelle `onCredential` avec un id_token OIDC — transmis TEL QUEL au
/// backend qui le VÉRIFIE (signature, émetteur, audience, expiration).
/// AUCUN secret ne transite ici : seul le Client ID PUBLIC (VITE_...) est lu.
///
/// Cas gérés : fermeture/annulation du sélecteur Google (CAS 4 → `onAbort`),
/// Client ID toujours résolu (env `VITE_GOOGLE_CLIENT_ID`, sinon repli public
/// intégré `DEFAULT_GOOGLE_CLIENT_ID`), échec de chargement du script Google
/// (repli explicite, jamais silencieux).
import { useEffect, useRef, useState } from 'react'

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string
            callback: (response: { credential?: string }) => void
            error_callback?: (error: { type: string }) => void
            auto_select?: boolean
          }) => void
          renderButton: (
            element: HTMLElement,
            options: {
              theme?: 'outline' | 'filled_blue' | 'filled_black'
              size?: 'large' | 'medium' | 'small'
              shape?: 'rectangular' | 'pill' | 'circle' | 'square'
              text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
              width?: number
            },
          ) => void
          cancel: () => void
        }
      }
    }
  }
}

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined

/** Repli intégré (Client ID PUBLIC FLICK — même valeur que VITE_GOOGLE_CLIENT_ID) :
 *  le bouton reste fonctionnel même sans `.env.local` (ex. preview, CI). */
export const DEFAULT_GOOGLE_CLIENT_ID =
  '465175507754-hli0c7t5d876elogv4h2a1t0c5dm0vld.apps.googleusercontent.com'

/** Client ID effectif : variable d'environnement, sinon repli intégré. */
export function resolveGoogleClientId(): string {
  const fromEnv = (GOOGLE_CLIENT_ID ?? '').trim()
  return fromEnv !== '' ? fromEnv : DEFAULT_GOOGLE_CLIENT_ID
}

const GSI_SCRIPT_SRC = 'https://accounts.google.com/gsi/client'

let scriptPromise: Promise<void> | null = null

function loadGsiScript(): Promise<void> {
  if (typeof window !== 'undefined' && window.google?.accounts?.id !== undefined) {
    return Promise.resolve()
  }
  if (scriptPromise !== null) {
    return scriptPromise
  }
  scriptPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = GSI_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptPromise = null
      reject(new Error('Chargement de Google impossible'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

interface GoogleButtonProps {
  onCredential: (idToken: string) => void
  onAbort?: () => void
  label?: string
}

export function GoogleButton({ onCredential, onAbort, label = 'Continuer avec Google' }: GoogleButtonProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const onCredentialRef = useRef(onCredential)
  const onAbortRef = useRef(onAbort)
  const [loadFailed, setLoadFailed] = useState(false)

  useEffect(() => {
    onCredentialRef.current = onCredential
    onAbortRef.current = onAbort
  })

  useEffect(() => {
    const clientId = resolveGoogleClientId()
    if (clientId === '') {
      return
    }
    let cancelled = false
    loadGsiScript()
      .then(() => {
        if (cancelled || containerRef.current === null || window.google === undefined) {
          return
        }
        // Ré-enregistré à chaque montage : le callback actif est toujours
        // celui de l'écran courant (connexion OU liaison — jamais mélangés).
        window.google.accounts.id.initialize({
          client_id: clientId,
            callback: (response) => {
              if (response.credential !== undefined && response.credential !== '') {
                onCredentialRef.current(response.credential)
              } else {
                onAbortRef.current?.()
              }
            },
            error_callback: () => {
              onAbortRef.current?.()
            },
            auto_select: false,
          })
        const width = containerRef.current.clientWidth > 0 ? containerRef.current.clientWidth : 320
        window.google.accounts.id.renderButton(containerRef.current, {
          theme: 'outline',
          size: 'large',
          shape: 'rectangular',
          text: 'continue_with',
          width,
        })
      })
      .catch(() => {
        if (!cancelled) {
          setLoadFailed(true)
        }
      })
    return () => {
      cancelled = true
      window.google?.accounts.id.cancel()
    }
  }, [])

  if (loadFailed) {
    return (
      <button
        type="button"
        disabled
        title="Connexion Google indisponible (réseau)"
        aria-label={label}
        className="flex h-13 min-h-11 w-full cursor-not-allowed items-center justify-center gap-2.5 rounded-xl border border-border-default bg-bg-surface py-3 text-sm font-semibold text-text-muted opacity-60"
      >
        <span aria-hidden="true" className="flex h-5 w-5 items-center justify-center rounded-full bg-white text-sm font-bold text-[#4285F4]">G</span>
        {label}
      </button>
    )
  }

  // Hauteur normalisée (bouton Google large ≈ 44px) + largeur fluide.
  return <div ref={containerRef} aria-label={label} className="flex min-h-11 w-full items-center justify-center" />
}
