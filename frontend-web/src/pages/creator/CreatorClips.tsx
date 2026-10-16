import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Input, Loader, useToast } from '../../components/ui'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDuration } from '../../utils/format'
import { clipsApi } from '../../services/api/clips'
import type { Clip, VideoVisibility } from '../../services/api/videos'

/// Mes clips (étape 15) : extraits découpés par moi (toutes chaînes sources).
/// Édition limitée (titre), visibilité, masquage — la chaîne source garde
/// la modération de fond.
export default function CreatorClips() {
  const toast = useToast()
  const [clips, setClips] = useState<Clip[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draftTitle, setDraftTitle] = useState('')
  const [saving, setSaving] = useState(false)

  const reload = useCallback(async () => {
    const page = await clipsApi.mine(0, 50)
    setClips(page.content)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
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

  async function handleRemove(clip: Clip) {
    if (!window.confirm(`Masquer « ${clip.title} » ?`)) return
    try {
      await clipsApi.remove(clip.id)
      setClips((current) => current.filter((c) => c.id !== clip.id))
      toast.show('Clip masqué', 'info')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Suppression impossible', 'error')
    }
  }

  async function handleVisibility(clip: Clip) {
    const next: VideoVisibility =
      clip.visibility === 'PUBLIC' ? 'UNLISTED' : clip.visibility === 'UNLISTED' ? 'PRIVATE' : 'PUBLIC'
    try {
      const updated = await clipsApi.changeVisibility(clip.id, next)
      setClips((current) => current.map((c) => (c.id === clip.id ? updated : c)))
      toast.show(`Clip « ${next === 'PUBLIC' ? 'public' : next === 'PRIVATE' ? 'privé' : 'non répertorié'} »`, 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Changement impossible', 'error')
    }
  }

  async function handleRename(clip: Clip) {
    if (draftTitle.trim().length === 0) return
    setSaving(true)
    try {
      const updated = await clipsApi.update(clip.id, {
        title: draftTitle.trim(),
        clipUrl: clip.clipUrl,
        thumbnailUrl: clip.thumbnailUrl,
        durationSeconds: clip.durationSeconds,
      })
      setClips((current) => current.map((c) => (c.id === clip.id ? updated : c)))
      setEditingId(null)
      toast.show('Titre mis à jour', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Renommage impossible', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="Chargement des clips">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Mes clips</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Mes clips</h1>
      <p className="text-sm text-text-secondary">
        Extraits que vous avez découpés — découpez depuis une VOD
        (bouton « Créer un clip » sur la page vidéo).
      </p>
      {clips.length === 0 ? (
        <Card>
          <p className="text-sm text-text-secondary">Aucun clip découpé pour le moment.</p>
        </Card>
      ) : (
        <div className="space-y-3">
          {clips.map((clip) => (
            <Card key={clip.id}>
              <div className="flex flex-wrap items-center gap-4">
                <div className="h-16 w-28 shrink-0 overflow-hidden rounded-lg bg-bg-elevated">
                  {clip.thumbnailUrl !== null && mediaUrl(clip.thumbnailUrl) !== null ? (
                    <img src={mediaUrl(clip.thumbnailUrl) ?? ''} alt="" loading="lazy" className="h-full w-full object-cover" />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center text-2xl" aria-hidden="true">✂️</div>
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  {editingId === clip.id ? (
                    <div className="flex flex-wrap items-center gap-2">
                      <Input
                        name={`clip-title-${clip.id}`}
                        aria-label="Titre du clip"
                        value={draftTitle}
                        onChange={(e) => setDraftTitle(e.target.value)}
                        maxLength={150}
                      />
                      <Button variant="primary" size="sm" loading={saving} onClick={() => handleRename(clip)}>
                        Enregistrer
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => setEditingId(null)}>
                        Annuler
                      </Button>
                    </div>
                  ) : (
                    <p className="truncate font-semibold text-text-primary">{clip.title}</p>
                  )}
                  <p className="mt-0.5 text-xs text-text-muted">
                    {formatDuration(clip.durationSeconds)} · {formatCount(clip.viewCount)} vues
                    {clip.videoId !== null && ' · VOD'}
                    {clip.streamId !== null && ' · Live'}
                    {' · '}lien /clip/{clip.id}
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    <Badge variant={clip.visibility === 'PUBLIC' ? 'accent' : 'warning'}>
                      {clip.visibility === 'PUBLIC' ? 'Public' : clip.visibility === 'PRIVATE' ? 'Privé' : 'Non répertorié'}
                    </Badge>
                    {clip.processingStatus !== 'READY' && (
                      <Badge variant="warning">
                        {clip.processingStatus === 'PROCESSING' ? 'Traitement' : 'Échec'}
                      </Badge>
                    )}
                  </div>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setEditingId(clip.id)
                      setDraftTitle(clip.title)
                    }}
                  >
                    Renommer
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => handleVisibility(clip)}>
                    Visibilité
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => handleRemove(clip)}>
                    Supprimer
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
