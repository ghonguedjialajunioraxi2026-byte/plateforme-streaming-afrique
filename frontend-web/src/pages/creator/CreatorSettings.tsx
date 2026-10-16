import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../features/auth/useAuth'
import { creatorsApi } from '../../services/api/creators'
import { categoriesApi, type Category } from '../../services/api/categories'
import type { ChannelDetail } from '../../types/domain'
import { mediaUrl } from '../../utils/media'
import { Avatar, Button, Card, Input, Skeleton, useToast } from '../../components/ui'
import { ApiError } from '../../services/http'

type Status = 'loading' | 'ready' | 'error'

/**
 * Configuration de chaîne (étape 09, §24).
 *
 * Sans chaîne : création (nom + bio + catégorie — PROMEUT en CREATOR).
 * Avec chaîne : identité (nom/bio/catégorie), visuels (avatar/bannière par
 * upload), fermeture logique (avec confirmation). Sections futures
 * (streaming, vidéos, analytics…) : liens vers leurs pages, PAS de logique.
 */
export default function CreatorSettings() {
  const { user, refreshSession } = useAuth()
  const { show } = useToast()
  const avatarRef = useRef<HTMLInputElement>(null)
  const bannerRef = useRef<HTMLInputElement>(null)

  const [status, setStatus] = useState<Status>('loading')
  const [error, setError] = useState<string | null>(null)
  const [channel, setChannel] = useState<ChannelDetail | null>(null)
  const [categories, setCategories] = useState<Category[]>([])

  const [displayName, setDisplayName] = useState('')
  const [bio, setBio] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState<'avatar' | 'banner' | null>(null)

  // userId en dépendance : voir Profile (restauration auth après montage).
  const userId = user?.id
  useEffect(() => {
    let cancelled = false
    async function load() {
      if (userId === undefined) {
        return
      }
      setStatus('loading')
      try {
        const [cats, mine] = await Promise.all([
          categoriesApi.list(),
          // Ma chaîne via JWT (GET /creators/me) : jamais de userId frontend.
          creatorsApi.getMyChannel().catch(() => null),
        ])
        if (cancelled) {
          return
        }
        setCategories(cats.content)
        if (mine !== null) {
          setChannel(mine)
          setDisplayName(mine.displayName)
          setBio(mine.bio ?? '')
          setCategoryId(mine.primaryCategory != null ? String(mine.primaryCategory.id) : '')
        }
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

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    const name = displayName.trim()
    if (name.length === 0) {
      setFieldError('Le nom de chaîne est obligatoire')
      return
    }
    if (user === null || user === undefined) {
      setFieldError('Connectez-vous pour gérer une chaîne')
      return
    }
    setFieldError(null)
    setSaving(true)
    try {
      const category = categoryId.length === 0 ? null : Number(categoryId)
      if (channel === null) {
        const created = await creatorsApi.create(user.id, {
          displayName: name,
          bio: bio.trim().length === 0 ? undefined : bio.trim(),
          primaryCategoryId: category,
        })
        setChannel(created)
        // La création PROMEUT en CREATOR : le JWT en mémoire porte encore
        // ROLE_USER → on renouvelle la session pour agir aussitôt au studio.
        try {
          await refreshSession()
        } catch {
          // Session conservée : l'utilisateur se reconnectera si besoin.
        }
        show('Chaîne créée — bienvenue parmi les créateurs', 'success')
      } else {
        const updated = await creatorsApi.update(channel.id, {
          displayName: name,
          bio: bio.trim().length === 0 ? undefined : bio.trim(),
          avatarUrl: channel.avatarUrl,
          bannerUrl: channel.bannerUrl,
          primaryCategoryId: category,
        })
        setChannel(updated)
        show('Chaîne mise à jour', 'success')
      }
    } catch (saveFailure) {
      setFieldError(
        saveFailure instanceof Error ? saveFailure.message : 'Enregistrement impossible',
      )
    } finally {
      setSaving(false)
    }
  }

  async function handleVisual(kind: 'avatar' | 'banner', file: File | undefined) {
    if (file === undefined || channel === null) {
      return
    }
    if (!file.type.startsWith('image/')) {
      show('Choisissez un fichier image (jpg, png, webp)', 'error')
      return
    }
    setUploading(kind)
    try {
      const updated =
        kind === 'avatar'
          ? await creatorsApi.uploadAvatar(channel.id, file)
          : await creatorsApi.uploadBanner(channel.id, file)
      setChannel(updated)
      show(kind === 'avatar' ? 'Avatar mis à jour' : 'Bannière mise à jour', 'success')
    } catch (uploadFailure) {
      show(
        uploadFailure instanceof ApiError
          ? uploadFailure.message
          : 'Envoi impossible',
        'error',
      )
    } finally {
      setUploading(null)
    }
  }

  if (status === 'loading') {
    return (
      <div className="mx-auto w-full max-w-3xl space-y-4" aria-busy="true" aria-label="Chargement">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="mx-auto w-full max-w-3xl" role="alert">
        <Card>
          <p className="font-semibold text-text-primary">Configuration inaccessible</p>
          <p className="mt-1 text-sm text-text-secondary">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-text-primary">
          {channel === null ? 'Créer ma chaîne' : 'Configuration de la chaîne'}
        </h1>
        <p className="mt-1 text-sm text-text-secondary">
          {channel === null
            ? 'Un nom suffit — votre compte devient créateur.'
            : `@${channel.slug} · ${channel.followerCount} abonné${channel.followerCount > 1 ? 's' : ''}`}
        </p>
      </div>

      <section aria-label="Identité visuelle">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">Identité visuelle</h2>
          {channel === null ? (
            <p className="mt-2 text-sm text-text-secondary">
              L'avatar et la bannière se règlent après la création.
            </p>
          ) : (
            <div className="mt-4 space-y-4">
              <div className="flex items-center gap-4">
                <Avatar src={mediaUrl(channel.avatarUrl) ?? undefined} name={channel.displayName} size="lg" />
                <input
                  ref={avatarRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="sr-only"
                  aria-label="Choisir l'avatar de la chaîne"
                  onChange={(event) => void handleVisual('avatar', event.target.files?.[0])}
                />
                <Button
                  variant="secondary"
                  size="sm"
                  loading={uploading === 'avatar'}
                  onClick={() => avatarRef.current?.click()}
                >
                  Changer l'avatar
                </Button>
              </div>
              <div>
                {mediaUrl(channel.bannerUrl) !== null ? (
                  <img
                    src={mediaUrl(channel.bannerUrl) ?? ''}
                    alt={`Bannière de ${channel.displayName}`}
                    className="aspect-[16/6] w-full rounded-xl border border-border-default object-cover"
                  />
                ) : (
                  <div className="aspect-[16/6] w-full rounded-xl border border-border-default bg-bg-elevated" aria-hidden="true" />
                )}
                <input
                  ref={bannerRef}
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  className="sr-only"
                  aria-label="Choisir la bannière de la chaîne (recommandé 16:6)"
                  onChange={(event) => void handleVisual('banner', event.target.files?.[0])}
                />
                <Button
                  variant="secondary"
                  size="sm"
                  loading={uploading === 'banner'}
                  onClick={() => bannerRef.current?.click()}
                  className="mt-2"
                >
                  Changer la bannière (recommandé 16:6)
                </Button>
              </div>
            </div>
          )}
        </Card>
      </section>

      <section aria-label="Informations de chaîne">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">
            Informations de chaîne
          </h2>
          <form onSubmit={handleSave} className="mt-4 space-y-4" noValidate>
            <Input
              label="Nom de la chaîne (slug public généré automatiquement)"
              name="displayName"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              maxLength={100}
              error={fieldError ?? undefined}
            />
            <div>
              <label htmlFor="bio" className="mb-1 block text-sm font-medium text-text-secondary">
                Description
              </label>
              <textarea
                id="bio"
                name="bio"
                rows={3}
                maxLength={500}
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                placeholder="De quoi parle votre chaîne ?"
                className="w-full rounded-lg border border-border-default bg-bg-surface px-4 py-3 text-text-primary placeholder:text-text-muted outline-none transition-colors focus:border-primary-500"
              />
            </div>
            <div>
              <label
                htmlFor="category"
                className="mb-1 block text-sm font-medium text-text-secondary"
              >
                Catégorie principale
              </label>
              <select
                id="category"
                name="category"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
                className="w-full rounded-lg border border-border-default bg-bg-surface px-4 py-3 text-text-primary outline-none transition-colors focus:border-primary-500"
              >
                <option value="">Aucune</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" loading={saving}>
              {channel === null ? 'Créer ma chaîne' : 'Enregistrer'}
            </Button>
          </form>
        </Card>
      </section>

      <section aria-label="Sections futures">
        <Card>
          <h2 className="font-display text-lg font-bold text-text-primary">La suite</h2>
          <p className="mt-2 text-sm text-text-secondary">
            Streaming, vidéos, clips, analytics, revenus, communauté et modération
            arrivent dans les prochaines étapes — leurs pages existent déjà :{' '}
            <Link to="/creator/stream" className="text-primary-400 hover:text-primary-300">
              studio
            </Link>
            .
          </p>
          {uploading !== null && (
            <p className="mt-2 flex items-center gap-2 text-sm text-text-secondary" aria-live="polite">
              Envoi de l'image en cours…
            </p>
          )}
        </Card>
      </section>
    </div>
  )
}
