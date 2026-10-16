import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { authApi } from '../../services/api/auth'
import { validateEmail } from '../../features/auth/validators'
import { Button, Input } from '../../components/ui'

export default function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const emailError = validateEmail(email)
    if (emailError !== null) {
      setFieldError(emailError)
      return
    }
    setFieldError(null)
    setSubmitting(true)
    try {
      await authApi.forgotPassword(email.trim())
      setSent(true)
    } catch {
      // Réponse backend toujours générique ; en cas d'erreur réseau, on
      // affiche le même message (aucune info sur l'existence du compte).
      setSent(true)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[80vh] items-center justify-center">
      <div className="w-full max-w-md space-y-6 rounded-xl border border-border-default bg-bg-surface p-8">
        <div className="text-center">
          <h1 className="font-display text-2xl font-bold text-text-primary">
            Mot de passe oublié
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Recevez un lien de récupération par email
          </p>
        </div>
        {sent ? (
          <p role="status" className="rounded-lg border border-primary-500/40 bg-primary-500/10 px-4 py-3 text-sm text-text-primary">
            Si un compte correspond à cette adresse, un email de récupération
            sera envoyé.
          </p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <Input
              label="Email"
              type="email"
              name="email"
              autoComplete="email"
              placeholder="votre@email.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldError ?? undefined}
            />
            <Button type="submit" fullWidth loading={submitting}>
              Envoyer le lien
            </Button>
          </form>
        )}
        <p className="text-center text-sm text-text-muted">
          <Link to="/connexion" className="text-primary-400 hover:text-primary-300">
            Retour à la connexion
          </Link>
        </p>
      </div>
    </div>
  )
}
