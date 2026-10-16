import { useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { authApi } from '../../services/api/auth'
import { profilesApi } from '../../services/api/profiles'
import { validatePassword } from '../../features/auth/validators'
import { useAuth } from '../../features/auth/useAuth'
import { GoogleButton } from '../../features/auth/GoogleButton'
import { Button, Card, Input, useToast } from '../../components/ui'
import { Icon, type IconName } from '../../components/ui/icons'
import { LanguageSelector } from '../../features/region/RegionSelectors'
import { useDataSaver } from '../../features/region/DataSaverContext'
import { useTheme } from '../../features/theme/ThemeContext'
import { useI18n } from '../../i18n'
import { ApiError } from '../../services/http'
import { cn } from '../../utils/cn'

function MenuRow({
  to,
  icon,
  label,
  detail,
  onClick,
}: {
  to?: string
  icon: IconName
  label: string
  detail?: string
  onClick?: () => void
}) {
  const content = (
    <>
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-elevated text-text-secondary">
        <Icon name={icon} className="h-[18px] w-[18px]" />
      </span>
      <span className="min-w-0 flex-1 truncate text-[15px] font-medium text-text-primary">
        {label}
      </span>
      {detail !== undefined && (
        <span className="shrink-0 text-[13px] text-text-muted">{detail}</span>
      )}
      <Icon name="chevron-down" className="h-4 w-4 -rotate-90 text-text-muted" />
    </>
  )
  const className =
    'flex min-h-11 w-full items-center gap-3 rounded-xl px-2 py-2 transition-colors active:bg-bg-hover'
  if (to !== undefined) {
    return (
      <Link to={to} onClick={onClick} className={className}>
        {content}
      </Link>
    )
  }
  return (
    <button type="button" onClick={onClick} className={cn(className, 'text-left')}>
      {content}
    </button>
  )
}

function MenuGroup({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title}>
      <h2 className="px-2 text-xs font-bold tracking-wider text-text-muted uppercase">
        {title}
      </h2>
      <div className="mt-1.5 divide-y divide-border-default rounded-2xl border border-border-default bg-bg-surface px-2 py-1">
        {children}
      </div>
    </section>
  )
}

/**
 * Paramètres du compte (étape 09, §33).
 *
 * Préférences fines (notifications, langue, confidentialité) : page /profile.
 * Ici : sécurité du mot de passe (changement connecté — TOUTES les sessions
 * sont révoquées côté backend, reconnexion requise) + raccourcis.
 */
export default function Settings() {
  const { show } = useToast()
  const { linkGoogle } = useAuth()
  const { lang, setLang, t } = useI18n()
  const { enabled: dataSaver, toggle: toggleDataSaver } = useDataSaver()
  const { theme, setTheme } = useTheme()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [linking, setLinking] = useState(false)
  const [linkError, setLinkError] = useState<string | null>(null)

  async function handleGoogleLink(idToken: string) {
    setLinkError(null)
    setLinking(true)
    try {
      await linkGoogle(idToken)
      show('Compte Google lié', 'success')
    } catch (linkFailure) {
      setLinkError(
        linkFailure instanceof ApiError
          ? linkFailure.message
          : 'Liaison Google impossible',
      )
    } finally {
      setLinking(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (currentPassword.length === 0) {
      setFieldError('Le mot de passe actuel est obligatoire')
      return
    }
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
    setSaving(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword, confirmPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
      show('Mot de passe changé — reconnectez-vous', 'success')
    } catch (changeFailure) {
      setServerError(
        changeFailure instanceof ApiError
          ? changeFailure.message
          : 'Changement impossible',
      )
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-[22px] font-bold tracking-tight text-text-primary md:text-2xl">
          Paramètres
        </h1>
        <p className="mt-1 text-sm text-text-secondary">
          Compte, sécurité et préférences.
        </p>
      </div>

      {/* ============ MOBILE — menu maquette ============ */}
      <div className="space-y-5 md:hidden">
        <MenuGroup title="Compte">
          <MenuRow to="/profile" icon="user" label="Mon profil" />
          <MenuRow
            icon="shield"
            label="Sécurité"
            onClick={() =>
              document.getElementById('mot-de-passe')?.scrollIntoView({ behavior: 'smooth' })
            }
          />
          <MenuRow to="/notifications" icon="bell" label="Notifications" />
        </MenuGroup>

        <MenuGroup title="Préférences">
          <div className="flex min-h-11 items-center gap-3 rounded-xl px-2 py-2">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-elevated text-text-secondary">
              <Icon name="video" className="h-[18px] w-[18px]" />
            </span>
            <label htmlFor="data-saver-mobile" className="min-w-0 flex-1 cursor-pointer">
              <span className="block truncate text-[15px] font-medium text-text-primary">
                Économie de données
              </span>
              <span className="block text-xs text-text-muted">Qualité réduite en 4G</span>
            </label>
            <button
              id="data-saver-mobile"
              type="button"
              role="switch"
              aria-checked={dataSaver}
              aria-label="Économie de données"
              onClick={toggleDataSaver}
              className={cn(
                'flex h-8 w-[52px] shrink-0 items-center rounded-full p-1 transition-colors duration-200',
                dataSaver ? 'justify-end bg-primary-500' : 'justify-start bg-bg-elevated',
              )}
            >
              <span className="h-6 w-6 rounded-full bg-white shadow" />
            </button>
          </div>
          <div className="flex min-h-11 items-center gap-3 rounded-xl px-2 py-2">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-elevated text-text-secondary">
              <Icon name="globe" className="h-[18px] w-[18px]" />
            </span>
            <span className="min-w-0 flex-1 truncate text-[15px] font-medium text-text-primary">
              Langue
            </span>
            <span className="w-28 shrink-0 text-[13px]">
              <LanguageSelector
                value={lang}
                onChange={(next) => {
                  setLang(next === 'en' ? 'en' : 'fr')
                  profilesApi.updateMe({ language: next }).catch(() => undefined)
                }}
              />
            </span>
          </div>
          <div className="flex min-h-11 items-center gap-3 rounded-xl px-2 py-2">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-elevated text-text-secondary">
              <Icon name="eye" className="h-[18px] w-[18px]" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[15px] font-medium text-text-primary">
                {t('settings.appearance')}
              </span>
              <span className="block text-xs text-text-muted">{t('settings.appearanceHelp')}</span>
            </span>
            <span
              role="radiogroup"
              aria-label={t('settings.appearance')}
              className="flex shrink-0 rounded-full border border-border-default bg-bg-elevated p-1"
            >
              {(['dark', 'light'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={theme === value}
                  aria-label={value === 'dark' ? t('settings.themeDark') : t('settings.themeLight')}
                  onClick={() => setTheme(value)}
                  className={cn(
                    'rounded-full px-3 py-1.5 text-[13px] font-semibold transition-colors',
                    theme === value
                      ? 'bg-primary-500 text-text-inverse shadow'
                      : 'text-text-secondary active:text-text-primary',
                  )}
                >
                  {value === 'dark' ? t('settings.themeDark') : t('settings.themeLight')}
                </button>
              ))}
            </span>
          </div>
        </MenuGroup>

        <MenuGroup title="Support">
          <MenuRow to="/assistant" icon="chat" label="Aide & FAQ" />
        </MenuGroup>
      </div>

      <section aria-label={t('settings.region')}>
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">{t('settings.region')}</h2>
          <div className="mt-4 space-y-4">
            <LanguageSelector
              value={lang}
              onChange={(next) => {
                setLang(next === 'en' ? 'en' : 'fr')
                // Persiste aussi côté profil (meilleur effort, jamais bloquant).
                profilesApi.updateMe({ language: next }).catch(() => undefined)
              }}
            />
            <label className="flex cursor-pointer items-start gap-3 text-sm" htmlFor="data-saver">
              <input
                id="data-saver"
                type="checkbox"
                checked={dataSaver}
                onChange={toggleDataSaver}
                className="mt-1 h-4 w-4"
              />
              <span>
                <span className="block font-medium text-text-primary">{t('settings.dataSaver')}</span>
                <span className="block text-xs text-text-secondary">{t('settings.dataSaverHelp')}</span>
              </span>
            </label>
          </div>
          <p className="mt-4 text-sm text-text-secondary">
            Pays, fuseau horaire et devise :{' '}
            <Link to="/profile" className="text-primary-400 hover:text-primary-300">
              voir mon profil
            </Link>
            .
          </p>
        </Card>
      </section>

      <section aria-label={t('settings.appearance')}>
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">
            {t('settings.appearance')}
          </h2>
          <p className="mt-1 text-sm text-text-secondary">{t('settings.appearanceHelp')}</p>
          <div
            role="radiogroup"
            aria-label={t('settings.appearance')}
            className="mt-4 grid grid-cols-2 gap-2 rounded-2xl border border-border-default bg-bg-elevated/40 p-1.5"
          >
            {(['dark', 'light'] as const).map((value) => {
              const active = theme === value
              return (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => setTheme(value)}
                  className={cn(
                    'flex items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition-colors',
                    active
                      ? 'bg-primary-500 text-text-inverse shadow-glow-primary'
                      : 'text-text-secondary hover:bg-bg-hover hover:text-text-primary',
                  )}
                >
                  <Icon name="eye" className="h-4 w-4" />
                  {value === 'dark' ? t('settings.themeDark') : t('settings.themeLight')}
                </button>
              )
            })}
          </div>
        </Card>
      </section>

      <section aria-label="Compte Google">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">
            Compte Google
          </h2>
          <p className="mt-1 text-sm text-text-secondary">
            Lie ton compte Google pour te connecter plus vite ensuite. La
            liaison exige cette session (ton mot de passe a déjà prouvé ton
            identité).
          </p>
          <div className="mt-4 max-w-sm">
            {linking ? (
              <p role="status" className="py-3 text-center text-sm text-text-secondary">
                Liaison en cours…
              </p>
            ) : (
              <GoogleButton onCredential={(token) => void handleGoogleLink(token)} />
            )}
          </div>
          {linkError !== null && (
            <p role="alert" className="mt-2 text-sm text-danger">
              {linkError}
            </p>
          )}
        </Card>
      </section>

      <section aria-label="Mot de passe" id="mot-de-passe" className="scroll-mt-20">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">
            Changer de mot de passe
          </h2>
          <p className="mt-1 text-sm text-text-secondary">
            Toutes vos sessions seront déconnectées : reconnectez-vous ensuite.
          </p>
          <form onSubmit={handleSubmit} className="mt-4 space-y-4" noValidate>
            <Input
              label="Mot de passe actuel"
              type="password"
              name="currentPassword"
              autoComplete="current-password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
            <Input
              label="Nouveau mot de passe (min 8, lettre + chiffre)"
              type="password"
              name="newPassword"
              autoComplete="new-password"
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
            <Input
              label="Confirmation"
              type="password"
              name="confirmPassword"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              error={fieldError ?? undefined}
            />
            {serverError !== null && (
              <p role="alert" className="text-sm text-danger">
                {serverError}
              </p>
            )}
            <Button type="submit" loading={saving}>
              Changer le mot de passe
            </Button>
          </form>
        </Card>
      </section>
    </div>
  )
}
