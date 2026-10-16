import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../features/auth/useAuth'
import { validateEmail } from '../../features/auth/validators'
import { GoogleButton } from '../../features/auth/GoogleButton'
import { Button, Input } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { APP_NAME } from '../../constants/app'
import { ApiError } from '../../services/http'

const inputClass =
  'w-full rounded-lg border border-border-default bg-bg-primary px-4 py-3 text-text-primary placeholder-text-muted outline-none transition-colors focus:border-primary-500'

export default function Login() {
  const { isAuthenticated, isLoading, login, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/home'
  const justRegistered = (location.state as { registered?: boolean } | null)?.registered === true

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [googleBusy, setGoogleBusy] = useState(false)

  if (!isLoading && isAuthenticated) {
    return <Navigate to="/home" replace />
  }

  async function handleSubmit(event: FormEvent) {    event.preventDefault()
    const emailError = validateEmail(email)
    if (emailError !== null) {
      setFieldError(emailError)
      return
    }
    if (password.length === 0) {
      setFieldError('Le mot de passe est obligatoire')
      return
    }
    setFieldError(null)
    setServerError(null)
    setSubmitting(true)
    try {
      await login(email.trim(), password)
      navigate(from, { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) {
        setServerError('Trop de tentatives, réessayez dans quelques instants')
      } else {
        setServerError(error instanceof Error ? error.message : 'Connexion impossible')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGoogleCredential(idToken: string) {
    setServerError(null)
    setGoogleBusy(true)
    try {
      await loginWithGoogle(idToken)
      navigate(from, { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        // CAS 3 : email déjà pris par un compte LOCAL — jamais de doublon.
        // L'utilisateur se connecte avec son mot de passe puis lie son
        // compte Google depuis les paramètres.
        setServerError(
          'Un compte existe déjà avec cet email. Connecte-toi avec ton mot de passe, puis lie ton compte Google depuis les paramètres.',
        )
      } else if (error instanceof ApiError && error.status === 429) {
        setServerError('Trop de tentatives, réessayez dans quelques instants')
      } else {
        setServerError(error instanceof Error ? error.message : 'Connexion Google impossible')
      }
    } finally {
      setGoogleBusy(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      {/* Mobile : plein écran sans carte (maquette) · Desktop : carte */}
      <div className="w-full max-w-md space-y-6 py-4 md:rounded-xl md:border md:border-border-default md:bg-bg-surface md:p-8">
        <div className="flex flex-col items-center gap-2 text-center">
          <h1 className="font-display text-[22px] font-bold tracking-tight text-text-primary">
            Bienvenue sur {APP_NAME}
          </h1>
          <p className="max-w-xs text-sm leading-relaxed text-text-secondary">
            Connecte-toi pour accéder à ton univers de streaming
          </p>
        </div>
        {justRegistered && (
          <p className="rounded-xl border border-primary-500/40 bg-primary-500/10 px-4 py-3 text-sm text-text-primary">
            Compte créé ! Connecte-toi, puis vérifie ton email depuis le lien reçu.
          </p>
        )}
        <form onSubmit={handleSubmit} className="space-y-3" noValidate>
          <div className="relative">
            <Icon
              name="user"
              className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-text-muted"
            />
            <Input
              type="email"
              name="email"
              autoComplete="email"
              placeholder="Email ou nom d'utilisateur"
              aria-label="Email ou nom d'utilisateur"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldError ?? undefined}
              className="h-13 border-border-default bg-bg-surface py-3.5 pl-11 md:bg-bg-primary"
            />
          </div>
          <div>
            <div className="relative">
              <Icon
                name="lock"
                className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-text-muted"
              />
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                placeholder="Mot de passe"
                aria-label="Mot de passe"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className={`${inputClass} h-13 border-border-default bg-bg-surface py-3.5 pr-12 pl-11 md:bg-bg-primary`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                className="absolute top-1/2 right-2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-lg text-text-muted transition-colors active:text-text-primary"
              >
                <Icon name="eye" className="h-[18px] w-[18px]" />
              </button>
            </div>
          </div>
          {serverError !== null && (
            <p role="alert" className="text-sm text-danger">
              {serverError}
            </p>
          )}
          <Button type="submit" fullWidth loading={submitting} disabled={isLoading} size="lg">
            Se connecter
          </Button>
        </form>
        <p className="text-center text-sm">
          <Link to="/mot-de-passe-oublie" className="font-medium text-primary-400 hover:text-primary-300">
            Mot de passe oublié ?
          </Link>
        </p>
        {/* Séparateur + connexion Google (maquette écran 2) :
            bouton officiel branché sur le backend (id_token vérifié
            côté serveur, JWT FLICK remis). */}
        <div className="flex items-center gap-3" aria-hidden="true">
          <span className="h-px flex-1 bg-border-default" />
          <span className="text-xs text-text-muted">ou</span>
          <span className="h-px flex-1 bg-border-default" />
        </div>
        <div className="space-y-2.5">
          {googleBusy ? (
            <p role="status" className="py-3 text-center text-sm text-text-secondary">
              Connexion Google en cours…
            </p>
          ) : (
            <GoogleButton onCredential={(token) => void handleGoogleCredential(token)} />
          )}
        </div>
        <p className="pb-2 text-center text-sm text-text-muted">
          Tu n'as pas de compte ?{' '}
          <Link to="/inscription" className="font-semibold text-primary-400 hover:text-primary-300">
            S'inscrire
          </Link>
        </p>
      </div>
    </div>
  )
}
