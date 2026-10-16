import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { authApi } from '../../services/api/auth'
import { passwordStrength, validatePassword } from '../../features/auth/validators'
import { Button, Input } from '../../components/ui'
import { ApiError } from '../../services/http'

export default function ResetPassword() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const passwordError = validatePassword(newPassword)
    if (passwordError !== null) {
      setFieldError(passwordError)
      return
    }
    if (newPassword !== confirmPassword) {
      setFieldError('La confirmation diffère du mot de passe')
      return
    }
    setFieldError(null)
    setServerError(null)
    setSubmitting(true)
    try {
      await authApi.resetPassword({ token, newPassword, confirmPassword })
      setDone(true)
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Réinitialisation impossible',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      <div className="w-full max-w-md space-y-6 rounded-xl border border-border-default bg-bg-surface p-8">
        <div className="text-center">
          <h1 className="font-display text-2xl font-bold text-text-primary">
            Nouveau mot de passe
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Choisissez un mot de passe fort (force {passwordStrength(newPassword)}/4)
          </p>
        </div>
        {token.length === 0 ? (
          <p role="alert" className="text-sm text-danger">
            Lien invalide : aucun token fourni.
          </p>
        ) : done ? (
          <div className="space-y-4 text-center">
            <p role="status" className="text-sm text-text-primary">
              Mot de passe réinitialisé. Reconnectez-vous avec le nouveau.
            </p>
            <Link to="/connexion" className="text-primary-400 hover:text-primary-300">
              Se connecter
            </Link>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <Input
              label="Nouveau mot de passe"
              type="password"
              name="newPassword"
              autoComplete="new-password"
              placeholder="••••••••"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <Input
              label="Confirmation"
              type="password"
              name="confirmPassword"
              autoComplete="new-password"
              placeholder="••••••••"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              error={fieldError ?? undefined}
            />
            {serverError !== null && (
              <p role="alert" className="text-sm text-danger">
                {serverError}
              </p>
            )}
            <Button type="submit" fullWidth loading={submitting}>
              Réinitialiser
            </Button>
          </form>
        )}
      </div>
    </div>
  )
}
