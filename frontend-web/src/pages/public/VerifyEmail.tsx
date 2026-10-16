import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '../../services/api/auth'

type State = 'pending' | 'success' | 'error'

export default function VerifyEmail() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [state, setState] = useState<State>(token.length === 0 ? 'error' : 'pending')
  const [message, setMessage] = useState<string>(
    token.length === 0 ? 'Lien invalide : aucun token fourni.' : '',
  )

  useEffect(() => {
    if (token.length === 0) {
      return
    }
    let cancelled = false
    authApi
      .verifyEmail(token)
      .then(() => {
        if (!cancelled) {
          setState('success')
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState('error')
          setMessage(error instanceof Error ? error.message : 'Vérification impossible')
        }
      })
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      <div className="w-full max-w-md space-y-6 rounded-xl border border-border-default bg-bg-surface p-8 text-center">
        <h1 className="font-display text-2xl font-bold text-text-primary">
          Vérification email
        </h1>
        {state === 'pending' && (
          <p className="text-sm text-text-secondary">Vérification en cours…</p>
        )}
        {state === 'success' && (
          <div className="space-y-4">
            <p role="status" className="text-sm text-text-primary">
              Adresse vérifiée, merci !
            </p>
            <Link to="/connexion" className="text-primary-400 hover:text-primary-300">
              Se connecter
            </Link>
          </div>
        )}
        {state === 'error' && (
          <div className="space-y-4">
            <p role="alert" className="text-sm text-danger">
              {message}
            </p>
            <Link to="/connexion" className="text-primary-400 hover:text-primary-300">
              Retour à la connexion
            </Link>
          </div>
        )}
      </div>
    </div>
  )
}
