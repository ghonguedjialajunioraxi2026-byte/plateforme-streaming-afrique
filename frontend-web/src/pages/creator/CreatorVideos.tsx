import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Badge, Button, Card, Input, Loader, Modal, useToast } from '../../components/ui'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDuration } from '../../utils/format'
import { creatorsApi } from '../../services/api/creators'
import { categoriesApi } from '../../services/api/categories'
import { videosApi, type Video, type VideoVisibility } from '../../services/api/videos'
import { http } from '../../services/http'

interface Category {
  id: number
  name: string
  slug: string
}

/// Mes vidéos (étape 15) : studio créateur — brouillons inclus, statuts visibles.
/// Publication (fichier téléversé ou URL externe), édition, visibilité, masquage.
export default function CreatorVideos() {
  const toast = useToast()
  const [videos, setVideos] = useState<Video[]>([])
  const [channelId, setChannelId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [publishOpen, setPublishOpen] = useState(false)

  const reload = useCallback(async () => {
    const page = await videosApi.mine(0, 50)
    setVideos(page.content)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const channel = await creatorsApi.getMyChannel()
        if (cancelled) return
        setChannelId(channel.id)
        await reload()
        if (!cancelled) setError(null)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Chargement impossible')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [reload])

  async function handleVisibility(video: Video, visibility: VideoVisibility) {
    try {
      const updated = await videosApi.changeVisibility(video.id, visibility)
      setVideos((current) => current.map((v) => (v.id === video.id ? updated : v)))
      toast.show('Visibilité modifiée', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Modification impossible', 'error')
    }
  }

  async function handleRemove(video: Video) {
    if (!window.confirm(`Masquer « ${video.title} » ? (clips désactivés, fichier conservé)`)) return
    try {
      await videosApi.remove(video.id)
      setVideos((current) => current.filter((v) => v.id !== video.id))
      toast.show('Vidéo masquée', 'info')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Suppression impossible', 'error')
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="Chargement des vidéos">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Mes vidéos</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
          <p className="mt-1 text-xs text-text-muted">Astuce : ouvrez d'abord votre chaîne depuis le tableau de bord.</p>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="font-display text-2xl font-bold text-text-primary">Mes vidéos</h1>
        <Button variant="primary" onClick={() => setPublishOpen(true)}>
          Publier une vidéo
        </Button>
      </div>
      {videos.length === 0 ? (
        <Card>
          <p className="text-sm text-text-secondary">
            Aucune vidéo — vos replays de live apparaîtront ici automatiquement après traitement.
          </p>
        </Card>
      ) : (
        <div className="space-y-3">
          {videos.map((video) => (
            <Card key={video.id}>
              <div className="flex flex-wrap items-center gap-4">
                <div className="h-16 w-28 shrink-0 overflow-hidden rounded-lg bg-bg-elevated">
                  {video.thumbnailUrl !== null && mediaUrl(video.thumbnailUrl) !== null ? (
                    <img src={mediaUrl(video.thumbnailUrl) ?? ''} alt="" loading="lazy" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-2xl" aria-hidden="true">🎬</div>
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold text-text-primary">{video.title}</p>
                  <p className="mt-0.5 text-xs text-text-muted">
                    {formatDuration(video.durationSeconds)} · {formatCount(video.viewCount)} vues
                    {video.originStreamId !== null && ' · Replay'}
                    {video.publishedAt === null && ' · Brouillon'}
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    <Badge variant={video.visibility === 'PUBLIC' ? 'live' : 'warning'}>
                      {video.visibility === 'PUBLIC' ? 'Publique' : video.visibility === 'PRIVATE' ? 'Privée' : 'Non répertoriée'}
                    </Badge>
                    {video.processingStatus !== 'READY' && (
                      <Badge variant="warning">
                        {video.processingStatus === 'PROCESSING' ? 'Traitement' : 'Échec'}
                      </Badge>
                    )}
                  </div>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {video.visibility !== 'PUBLIC' ? (
                    <Button variant="secondary" size="sm" onClick={() => handleVisibility(video, 'PUBLIC')}>
                      Publier
                    </Button>
                  ) : (
                    <Button variant="ghost" size="sm" onClick={() => handleVisibility(video, 'PRIVATE')}>
                      Masquer
                    </Button>
                  )}
                  <Button variant="ghost" size="sm" onClick={() => handleRemove(video)}>
                    Supprimer
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
      {channelId !== null && (
        <PublishModal
          open={publishOpen}
          onClose={() => setPublishOpen(false)}
          channelId={channelId}
          onPublished={(video) => {
            setVideos((current) => [video, ...current])
            setPublishOpen(false)
            toast.show('Vidéo publiée', 'success')
          }}
        />
      )}
    </div>
  )
}

/// Publication : fichier téléversé (MP4/WebM) OU URL externe + métadonnées.
function PublishModal({
  open,
  onClose,
  channelId,
  onPublished,
}: {
  open: boolean
  onClose: () => void
  channelId: number
  onPublished: (video: Video) => void
}) {
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [categories, setCategories] = useState<Category[]>([])
  const [videoUrl, setVideoUrl] = useState('')
  const [thumbnailUrl, setThumbnailUrl] = useState('')
  const [duration, setDuration] = useState('60')
  const [file, setFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    categoriesApi.list(undefined, 0, 50).then(
      (page) => setCategories(page.content),
      () => undefined,
    )
  }, [open ])

  async function handleUpload() {
    if (file === null) return
    setUploading(true)
    setFormError(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const uploaded = await http.postForm<{ url: string; storageKey: string; sizeBytes: number; contentType: string }>(
        '/uploads/videos',
        formData,
      )
      setVideoUrl(uploaded.url)
      toast.show('Fichier téléversé', 'success')
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Téléversement impossible'
      setFormError(message)
    } finally {
      setUploading(false)
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (title.trim().length === 0) {
      setFormError('Le titre est obligatoire')
      return
    }
    if (videoUrl.trim().length === 0) {
      setFormError('Téléversez un fichier ou collez une URL externe')
      return
    }
    setFormError(null)
    setSubmitting(true)
    try {
      const video = await videosApi.publish(channelId, {
        title: title.trim(),
        description: description.trim().length > 0 ? description.trim() : undefined,
        categoryId: categoryId === '' ? null : Number(categoryId),
        videoUrl: videoUrl.trim(),
        thumbnailUrl: thumbnailUrl.trim().length > 0 ? thumbnailUrl.trim() : null,
        durationSeconds: Math.max(1, Number.parseInt(duration, 10) || 1),
      })
      onPublished(video)
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Publication impossible')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Publier une vidéo">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input label="Titre" name="video-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={150} />
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-text-primary">Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={2000}
            rows={3}
            className="w-full rounded-lg border border-border-default bg-bg-primary px-4 py-3 text-text-primary outline-none focus:border-primary-500"
          />
        </label>
        <div className="grid grid-cols-2 gap-3">
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-primary">Catégorie</span>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="w-full rounded-lg border border-border-default bg-bg-primary px-4 py-3 text-text-primary outline-none focus:border-primary-500"
            >
              <option value="">Aucune</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <Input
            label="Durée (secondes)"
            name="video-duration"
            type="number"
            min={1}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
          />
        </div>
        <div className="rounded-lg border border-border-default p-3">
          <p className="text-sm font-medium text-text-primary">Fichier (MP4/WebM, 512 Mo max)</p>
          <div className="mt-2 flex gap-2">
            <input
              type="file"
              accept="video/mp4,video/webm"
              aria-label="Fichier vidéo"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              className="min-w-0 flex-1 text-sm text-text-secondary"
            />
            <Button variant="secondary" size="sm" onClick={handleUpload} loading={uploading} disabled={file === null || uploading}>
              Envoyer
            </Button>
          </div>
        </div>
        <Input label="URL externe (alternative au fichier)" name="video-url" value={videoUrl} onChange={(e) => setVideoUrl(e.target.value)} placeholder="https://…/video.mp4" />
        <Input label="Miniature (URL)" name="video-thumb" value={thumbnailUrl} onChange={(e) => setThumbnailUrl(e.target.value)} placeholder="https://…/vignette.jpg" />
        {formError !== null && (
          <p className="text-sm text-danger" role="alert">{formError}</p>
        )}
        <Button type="submit" variant="primary" fullWidth loading={submitting} disabled={submitting}>
          Publier
        </Button>
      </form>
    </Modal>
  )
}
