import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useAuth } from '../../features/auth/useAuth'
import {
  passwordStrength,
  validateEmail,
  validatePassword,
  validateUsername,
} from '../../features/auth/validators'
import { GoogleButton } from '../../features/auth/GoogleButton'
import { Button, Input } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { ApiError } from '../../services/http'

const STRENGTH_LABELS = ['—', 'Faible', 'Correct', 'Bon', 'Excellent']
const STRENGTH_COLORS = [
  'bg-border-default',
  'bg-danger',
  'bg-accent-500',
  'bg-primary-500',
  'bg-success',
]

const fieldClass =
  'h-13 border-border-default bg-bg-surface py-3.5 pl-11 md:bg-bg-primary'

function FieldIcon({ name }: { name: 'user' | 'mail' | 'lock' }) {
  return (
    <Icon
      name={name}
      className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-text-muted"
    />
  )
}

export default function Register() {
  const { isAuthenticated, isLoading, register, loginWithGoogle } = useAuth()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [googleBusy, setGoogleBusy] = useState(false)

  if (!isLoading && isAuthenticated) {
    return <Navigate to="/home" replace />
  }

  const strength = passwordStrength(password)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const firstError =
      validateUsername(username) ??
      validateEmail(email) ??
      validatePassword(password) ??
      (password === confirmPassword ? null : 'La confirmation diffère du mot de passe') ??
      (acceptedTerms ? null : 'Tu dois accepter les conditions d\u2019utilisation')
    if (firstError !== null) {
      setFieldError(firstError)
      return
    }
    setFieldError(null)
    setServerError(null)
    setSubmitting(true)
    try {
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
        inviteCode: inviteCode.trim() === '' ? undefined : inviteCode.trim(),
      })
      navigate('/connexion', { replace: true, state: { registered: true } })
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setServerError(error.message)
      } else if (error instanceof ApiError && error.status === 429) {
        setServerError('Trop de tentatives, réessayez dans quelques instants')
      } else {
        setServerError(error instanceof Error ? error.message : 'Inscription impossible')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleGoogleCredential(idToken: string) {
    if (!acceptedTerms) {
      setServerError('Accepte les conditions d\u2019utilisation pour continuer avec Google')
      return
    }
    setServerError(null)
    setGoogleBusy(true)
    try {
      // 1er passage Google = création automatique du compte (CAS 1 backend),
      // déjà connu = connexion directe (CAS 2). Même session JWT que le classique.
      await loginWithGoogle(idToken)
      navigate('/home', { replace: true })
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
        setServerError(error instanceof Error ? error.message : 'Inscription Google impossible')
      }
    } finally {
      setGoogleBusy(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      {/* Mobile : plein écran sans carte (maquette) · Desktop : carte */}
      <div className="w-full max-w-md space-y-5 py-4 md:rounded-xl md:border md:border-border-default md:bg-bg-surface md:p-8">
        <div className="flex flex-col items-center gap-2 text-center">
          <h1 className="font-display text-[22px] font-bold tracking-tight text-text-primary">
            Créer un compte
          </h1>
          <p className="max-w-xs text-sm leading-relaxed text-text-secondary">
            Rejoins la communauté FLICK
          </p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3" noValidate>
          <div className="relative">
            <FieldIcon name="user" />
            <Input
              type="text"
              name="username"
              autoComplete="username"
              placeholder="Nom d'utilisateur"
              aria-label="Nom d'utilisateur"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className={fieldClass}
            />
          </div>
          <div className="relative">
            <FieldIcon name="mail" />
            <Input
              type="email"
              name="email"
              autoComplete="email"
              placeholder="Email"
              aria-label="Email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className={fieldClass}
            />
          </div>
          <div className="relative">
            <FieldIcon name="lock" />
            <Input
              type="password"
              name="password"
              autoComplete="new-password"
              placeholder="Mot de passe"
              aria-label="Mot de passe"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={fieldClass}
            />
          </div>
          {password.length > 0 && (
            <div aria-live="polite">
              <div className="flex gap-1">
                {[0, 1, 2, 3].map((index) => (
                  <div
                    key={index}
                    className={`h-1.5 flex-1 rounded-full ${
                      index < strength ? STRENGTH_COLORS[strength] : 'bg-border-default'
                    }`}
                  />
                ))}
              </div>
              <p className="mt-1 text-xs text-text-muted">
                Force : {STRENGTH_LABELS[strength]} (min 8 caractères, lettre + chiffre)
              </p>
            </div>
          )}
          <div className="relative">
            <FieldIcon name="lock" />
            <Input
              type="password"
              name="confirmPassword"
              autoComplete="new-password"
              placeholder="Confirmer le mot de passe"
              aria-label="Confirmer le mot de passe"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              error={fieldError ?? undefined}
              className={fieldClass}
            />
          </div>
          <div className="relative">
            <Input
              type="text"
              name="inviteCode"
              autoComplete="off"
              placeholder="Code d'invitation (bêta privée, si reçu)"
              aria-label="Code d'invitation"
              value={inviteCode}
              onChange={(event) => setInviteCode(event.target.value)}
              className={fieldClass}
            />
          </div>
          {serverError !== null && (
            <p role="alert" className="text-sm text-danger">
              {serverError}
            </p>
          )}
          <Button type="submit" fullWidth loading={submitting} disabled={isLoading} size="lg">
            S'inscrire
          </Button>
        </form>
        {/* Séparateur + inscription sociale (même GoogleButton que /connexion :
            1er passage = création auto du compte, passagem suivants = connexion). */}
        <div className="flex items-center gap-3" aria-hidden="true">
          <span className="h-px flex-1 bg-border-default" />
          <span className="text-xs text-text-muted">ou</span>
          <span className="h-px flex-1 bg-border-default" />
        </div>
        {googleBusy ? (
          <p role="status" className="py-3 text-center text-sm text-text-secondary">
            Inscription Google en cours…
          </p>
        ) : (
          <GoogleButton
            label="S'inscrire avec Google"
            onCredential={(token) => void handleGoogleCredential(token)}
          />
        )}
        <p className="text-center text-xs leading-relaxed text-text-muted">
          <label className="flex cursor-pointer items-start justify-center gap-2">
            <input
              type="checkbox"
              checked={acceptedTerms}
              onChange={(event) => setAcceptedTerms(event.target.checked)}
              className="mt-0.5 h-4 w-4 accent-primary-500"
            />
            <span>
              En créant un compte, j&apos;accepte les{' '}
              <Link to="/conditions" className="text-primary-400 hover:underline">Conditions d&apos;utilisation</Link>
              {' '}et la <Link to="/confidentialite" className="text-primary-400 hover:underline">Politique de confidentialité</Link>
              {' '}(<Link to="/regles" className="text-primary-400 hover:underline">règles communautaires</Link>).
            </span>
          </label>
        </p>
        <p className="pb-2 text-center text-sm text-text-muted">
          Déjà un compte ?{' '}
          <Link to="/connexion" className="font-semibold text-primary-400 hover:text-primary-300">
            Se connecter
          </Link>
        </p>
      </div>
    </div>
  )
}
