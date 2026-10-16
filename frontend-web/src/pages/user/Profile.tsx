import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../features/auth/useAuth'
import { profilesApi } from '../../services/api/profiles'
import { creatorsApi } from '../../services/api/creators'
import { regionApi, type Country } from '../../services/api/region'
import type { MyProfile } from '../../types/domain'
import { mediaUrl } from '../../utils/media'
import { Avatar, Button, Card, Input, Skeleton, useToast } from '../../components/ui'
import { CountrySelector, CurrencySelector, LanguageFilter } from '../../features/region/RegionSelectors'
import { useI18n } from '../../i18n'
import { ApiError } from '../../services/http'

type Status = 'loading' | 'ready' | 'saving' | 'error'

/**
 * Profil de l'utilisateur connecté (étape 09, §22-23).
 *
 * Voir (avatar, identité, bio, liens, email vérifié) + modifier
 * (général, bio, pays, langue, site, réseaux, naissance, préférences) +
 * avatar (upload + preview locale) + accès chaîne (voir/créer) et paramètres.
 * États : loading (Skeleton), error/empty, success (Toast).
 */
export default function Profile() {
  const { user } = useAuth()
  const { show } = useToast()
  const fileRef = useRef<HTMLInputElement>(null)

  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [profile, setProfile] = useState<MyProfile | null>(null)
  const [hasChannel, setHasChannel] = useState(false)

  const [displayName, setDisplayName] = useState('')
  const [bio, setBio] = useState('')
  const [countryCode, setCountryCode] = useState('')
  const [language, setLanguage] = useState('')
  const [timezone, setTimezone] = useState('')
  const [preferredCurrency, setPreferredCurrency] = useState('')
  const [countries, setCountries] = useState<Country[]>([])
  const [locales, setLocales] = useState<string[]>([])
  const [currencies, setCurrencies] = useState<string[]>([])
  const { setLang } = useI18n()
  const [websiteUrl, setWebsiteUrl] = useState('')
  const [xHandle, setXHandle] = useState('')
  const [instagramHandle, setInstagramHandle] = useState('')
  const [youtubeUrl, setYoutubeUrl] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [notifyLive, setNotifyLive] = useState(true)
  const [notifyContent, setNotifyContent] = useState(true)
  const [isPublic, setIsPublic] = useState(true)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  const [preview, setPreview] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  // userId en dépendance : la restauration auth (async) arrive après le
  // montage ; sans elle, la page resterait vide. Sous ProtectedRoute,
  // l'utilisateur finit toujours défini (sinon redirection /connexion).
  const userId = user?.id
  useEffect(() => {
    let cancelled = false
    async function load() {
      if (userId === undefined) {
        return
      }
      setStatus('loading')
      setError(null)
      try {
        const [me, channel, regionCountries, regionLocales, regionCurrencies] = await Promise.all([
          profilesApi.getMe(),
          // Présence d'une chaîne via JWT (GET /creators/me).
          creatorsApi.getMyChannel().catch(() => null),
          regionApi.countries().catch(() => [] as Country[]),
          regionApi.locales().catch(() => [] as string[]),
          regionApi.currencies().catch(() => [] as string[]),
        ])
        if (cancelled) {
          return
        }
        setProfile(me)
        setDisplayName(me.displayName)
        setBio(me.bio ?? '')
        setCountryCode(me.countryCode ?? '')
        setLanguage(me.language ?? '')
        setTimezone(me.timezone ?? '')
        setPreferredCurrency(me.preferredCurrency ?? '')
        setCountries(regionCountries)
        setLocales(regionLocales)
        setCurrencies(regionCurrencies)
        setWebsiteUrl(me.websiteUrl ?? '')
        setXHandle(me.xHandle ?? '')
        setInstagramHandle(me.instagramHandle ?? '')
        setYoutubeUrl(me.youtubeUrl ?? '')
        setBirthDate(me.birthDate ?? '')
        setNotifyLive(me.notifyLive)
        setNotifyContent(me.notifyContent)
        setIsPublic(me.isPublic)
        setHasChannel(channel !== null)
        setStatus('ready')
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : 'Chargement impossible')
          setStatus('error')
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [userId])

  // Libère l'URL de preview locale (pas de fuite mémoire).
  useEffect(() => {
    return () => {
      if (preview !== null) {
        URL.revokeObjectURL(preview)
      }
    }
  }, [preview])

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    setSaveError(null)
    setSaving(true)
    try {
      const updated = await profilesApi.updateMe({
        displayName: displayName.trim(),
        bio: bio.trim().length === 0 ? null : bio.trim(),
        countryCode: countryCode.trim().length === 0 ? null : countryCode.trim(),
        language: language.trim().length === 0 ? null : language.trim(),
        timezone: timezone.trim().length === 0 ? null : timezone.trim(),
        preferredCurrency: preferredCurrency.trim().length === 0 ? null : preferredCurrency.trim(),
        websiteUrl: websiteUrl.trim().length === 0 ? null : websiteUrl.trim(),
        xHandle: xHandle.trim().length === 0 ? null : xHandle.trim(),
        instagramHandle: instagramHandle.trim().length === 0 ? null : instagramHandle.trim(),
        youtubeUrl: youtubeUrl.trim().length === 0 ? null : youtubeUrl.trim(),
        birthDate: birthDate.length === 0 ? null : birthDate,
        notifyLive,
        notifyContent,
        isPublic,
      })
      setProfile(updated)
      // La langue du profil (fr/en) pilote aussi l'interface.
      if (updated.language === 'fr' || updated.language === 'en') {
        setLang(updated.language)
      }
      show('Profil mis à jour', 'success')
    } catch (saveFailure) {
      if (saveFailure instanceof ApiError && saveFailure.errors !== undefined) {
        setSaveError(Object.values(saveFailure.errors)[0] ?? saveFailure.message)
      } else {
        setSaveError(saveFailure instanceof Error ? saveFailure.message : 'Enregistrement impossible')
      }
    } finally {
      setSaving(false)
    }
  }

  async function handleAvatar(file: File | undefined) {
    if (file === undefined) {
      return
    }
    if (!file.type.startsWith('image/')) {
      show('Choisissez un fichier image (jpg, png, webp)', 'error')
      return
    }
    const local = URL.createObjectURL(file)
    setPreview((previous) => {
      if (previous !== null) {
        URL.revokeObjectURL(previous)
      }
      return local
    })
    setUploading(true)
    try {
      const updated = await profilesApi.uploadAvatar(file)
      setProfile(updated)
      show('Avatar mis à jour', 'success')
    } catch (uploadFailure) {
      setPreview(null)
      show(
        uploadFailure instanceof Error ? uploadFailure.message : 'Envoi impossible',
        'error',
      )
    } finally {
      setUploading(false)
    }
  }

  if (status === 'loading') {
    return (
      <div className="mx-auto w-full max-w-3xl space-y-4" aria-busy="true" aria-label="Chargement du profil">
        <Skeleton className="h-24 w-24 rounded-full" />
        <Skeleton className="h-6 w-48" />
        <Skeleton className="h-32 w-full rounded-xl" />
      </div>
    )
  }

  if (status === 'error' || profile === null) {
    return (
      <div className="mx-auto w-full max-w-3xl" role="alert">
        <Card>
          <p className="font-semibold text-text-primary">Profil inaccessible</p>
          <p className="mt-1 text-sm text-text-secondary">{error ?? 'Erreur inconnue.'}</p>
          <Button variant="secondary" className="mt-4" onClick={() => window.location.reload()}>
            Réessayer
          </Button>
        </Card>
      </div>
    )
  }

  const avatarSrc = preview ?? mediaUrl(profile.avatarUrl) ?? undefined

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <section aria-label="Aperçu du profil">
        <Card>
          <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center">
            <Avatar src={avatarSrc} name={profile.displayName} size="xl" />
            <div className="min-w-0 flex-1">
              <h1 className="font-display text-2xl font-bold text-text-primary">
                {profile.displayName}
              </h1>
              <p className="text-sm text-text-muted">@{profile.username}</p>
              {profile.bio && <p className="mt-2 text-sm text-text-secondary">{profile.bio}</p>}
              <p className="mt-2 text-xs text-text-muted">
                {profile.email} · {profile.emailVerified ? 'Email vérifié' : 'Email non vérifié'}
              </p>
            </div>
            <div className="flex shrink-0 gap-2">
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="sr-only"
                aria-label="Choisir un nouvel avatar"
                onChange={(event) => void handleAvatar(event.target.files?.[0])}
              />
              <Button
                variant="secondary"
                size="sm"
                loading={uploading}
                onClick={() => fileRef.current?.click()}
              >
                Changer l'avatar
              </Button>
            </div>
          </div>
        </Card>
      </section>

      <section aria-label="Modifier le profil">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">
            Informations générales
          </h2>
          <form onSubmit={handleSave} className="mt-4 space-y-4" noValidate>
            <Input
              label="Nom affiché"
              name="displayName"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              maxLength={100}
            />
            <div>
              <label htmlFor="bio" className="mb-1 block text-sm font-medium text-text-secondary">
                Bio
              </label>
              <textarea
                id="bio"
                name="bio"
                rows={3}
                maxLength={500}
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                placeholder="Parlez de vous en quelques mots"
                className="w-full rounded-lg border border-border-default bg-bg-surface px-4 py-3 text-text-primary placeholder:text-text-muted outline-none transition-colors focus:border-primary-500"
              />
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <CountrySelector
                countries={countries}
                value={countryCode}
                onChange={setCountryCode}
              />
              {locales.length > 0 ? (
                <LanguageFilter
                  languages={locales}
                  value={language}
                  onChange={setLanguage}
                />
              ) : (
                <Input
                  label="Langue (ex. fr)"
                  name="language"
                  value={language}
                  onChange={(event) => setLanguage(event.target.value)}
                  maxLength={10}
                />
              )}
            </div>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Fuseau horaire (ex. Africa/Douala)"
                name="timezone"
                value={timezone}
                onChange={(event) => setTimezone(event.target.value)}
                maxLength={60}
              />
              <CurrencySelector
                currencies={currencies}
                value={preferredCurrency}
                onChange={setPreferredCurrency}
              />
            </div>
            <Input
              label="Site personnel"
              name="websiteUrl"
              type="url"
              inputMode="url"
              placeholder="https://…"
              value={websiteUrl}
              onChange={(event) => setWebsiteUrl(event.target.value)}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Input
                label="Pseudo X"
                name="xHandle"
                value={xHandle}
                onChange={(event) => setXHandle(event.target.value)}
                maxLength={100}
              />
              <Input
                label="Pseudo Instagram"
                name="instagramHandle"
                value={instagramHandle}
                onChange={(event) => setInstagramHandle(event.target.value)}
                maxLength={100}
              />
            </div>
            <Input
              label="Chaîne YouTube (URL)"
              name="youtubeUrl"
              type="url"
              inputMode="url"
              placeholder="https://…"
              value={youtubeUrl}
              onChange={(event) => setYoutubeUrl(event.target.value)}
            />
            <Input
              label="Date de naissance (privée, jamais publique)"
              name="birthDate"
              type="date"
              value={birthDate}
              onChange={(event) => setBirthDate(event.target.value)}
            />
            <fieldset>
              <legend className="mb-2 text-sm font-medium text-text-secondary">
                Confidentialité et alertes
              </legend>
              <div className="space-y-2 text-sm text-text-primary">
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={isPublic}
                    onChange={(event) => setIsPublic(event.target.checked)}
                  />
                  Profil visible publiquement
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={notifyLive}
                    onChange={(event) => setNotifyLive(event.target.checked)}
                  />
                  M'alerter des directs suivis
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={notifyContent}
                    onChange={(event) => setNotifyContent(event.target.checked)}
                  />
                  M'alerter des nouveaux contenus
                </label>
              </div>
            </fieldset>
            {saveError !== null && (
              <p role="alert" className="text-sm text-danger">
                {saveError}
              </p>
            )}
            <Button type="submit" loading={saving}>
              Enregistrer
            </Button>
          </form>
        </Card>
      </section>

      <section aria-label="Chaîne et paramètres">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">Chaîne</h2>
          {hasChannel ? (
            <p className="mt-2 text-sm text-text-secondary">
              Vous avez une chaîne.{' '}
              <Link to="/creator/settings" className="text-primary-400 hover:text-primary-300">
                La configurer
              </Link>
            </p>
          ) : (
            <p className="mt-2 text-sm text-text-secondary">
              Pas encore de chaîne.{' '}
              <Link to="/creator/settings" className="text-primary-400 hover:text-primary-300">
                Créer ma chaîne
              </Link>
            </p>
          )}
          <p className="mt-2 text-sm text-text-secondary">
            <Link to="/settings" className="text-primary-400 hover:text-primary-300">
              Paramètres du compte
            </Link>
          </p>
        </Card>
      </section>
    </div>
  )
}
