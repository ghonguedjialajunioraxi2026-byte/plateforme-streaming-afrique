import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { VideoPlayer } from '../../components/streaming/VideoPlayer'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate, formatDuration } from '../../utils/format'
import { creatorsApi } from '../../services/api/creators'
import { clipsApi } from '../../services/api/clips'
import type { Clip, Playback } from '../../services/api/videos'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Page clip (étape 15) : /clip/:id et lien court /c/:slug (§40-41).
/// Lecteur (démarrage à l'extrait) + métadonnées + auteur + partage + source.
export default function ClipView() {
  const { id = '', slug = '' } = useParams<{ id?: string; slug?: string }>()
  const navigate = useNavigate()
  const toast = useToast()
  const clipId = Number(id)

  const [clip, setClip] = useState<Clip | null>(null)
  const [playback, setPlayback] = useState<Playback | null>(null)
  const [views, setViews] = useState<number | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'not-found' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [channelSlug, setChannelSlug] = useState<string | null>(null)
  const viewSent = useRef(false)
  usePageMeta(
    clip?.title ?? null,
    clip !== null ? `Extrait : ${clip.title} — clip à partager` : null,
  )

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStatus('loading')
      viewSent.current = false
      try {
        const detail = slug !== '' ? await clipsApi.getByShareSlug(slug) : await clipsApi.get(clipId)
        const resolvedId = detail.id
        if (cancelled) return
        setClip(detail)
        setViews(detail.viewCount)
        setStatus('ready')
        creatorsApi.getById(detail.creatorId).then(
          (channel) => {
            if (!cancelled) setChannelSlug(channel.slug)
          },
          () => undefined,
        )
        try {
          const urls = await clipsApi.playback(resolvedId)
          if (!cancelled) setPlayback(urls)
        } catch {
          // Playback indisponible (en traitement) : page lisible.
        }
        if (!viewSent.current) {
          viewSent.current = true
          try {
            const count = await clipsApi.recordView(resolvedId)
            if (!cancelled) setViews(count)
          } catch {
            // Silencieux (privé, anti-abus).
          }
        }
      } catch (loadError) {
        if (cancelled) return
        const message = loadError instanceof Error ? loadError.message : ''
        if (message.includes('introuvable')) {
          setStatus('not-found')
        } else {
          setError(message.length > 0 ? message : 'Chargement impossible')
          setStatus('error')
        }
      }
    }
    if (Number.isFinite(clipId) || slug !== '') {
      void load()
    } else {
      setStatus('not-found')
    }
    return () => {
      cancelled = true
    }
  }, [clipId, slug])

  async function handleShare() {
    const url = `${window.location.origin}/c/${clip?.shareSlug ?? ''}`
    try {
      await navigator.clipboard.writeText(url)
      toast.show('Lien court copié', 'success')
    } catch {
      toast.show(url, 'info')
    }
  }

  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-5xl space-y-4" aria-busy="true" aria-label="Chargement du clip">
        <div className="aspect-video w-full animate-pulse rounded-xl bg-bg-elevated" />
        <div className="h-6 w-2/3 animate-pulse rounded bg-bg-elevated" />
      </div>
    )
  }

  if (status === 'not-found') {
    return (
      <div className="mx-auto max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">404 — Clip introuvable</p>
          <p className="mt-1 text-sm text-text-secondary">
            Ce clip n'existe pas, plus, ou n'est pas visible (privé, en traitement, supprimé).
          </p>
        </Card>
      </div>
    )
  }

  if (status === 'error' || clip === null) {
    return (
      <div className="mx-auto max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">Clip inaccessible</p>
          <p className="mt-1 text-sm text-text-secondary">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      </div>
    )
  }

  const playable = playback?.playbackUrl ?? clip.clipUrl
  const poster = clip.thumbnailUrl !== null ? (mediaUrl(clip.thumbnailUrl) ?? undefined) : undefined

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <VideoPlayer
        src={playable}
        poster={poster}
        startAtSeconds={playback?.startsAtSeconds ?? clip.startsAtSeconds}
      />
      {clip.processingStatus !== 'READY' && (
        <Card className="border-warning/40">
          <p className="text-sm text-text-primary" role="status">
            Votre clip est en cours de préparation… il sera lisible dans quelques instants.
          </p>
          <div className="mt-2 flex items-center gap-2 text-sm text-text-muted" aria-live="polite">
            <Loader size="sm" /> Traitement en cours
          </div>
        </Card>
      )}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="accent">Clip</Badge>
            <h1 className="font-display text-xl font-bold text-text-primary">{clip.title}</h1>
          </div>
          <p className="mt-1 text-sm text-text-secondary">
            {views !== null ? `${formatCount(views)} vues` : '…'} · {formatDate(clip.createdAt)}
            {' · '}{formatDuration(clip.durationSeconds)}
            {clip.videoId !== null && <span> · Extrait de VOD</span>}
            {clip.streamId !== null && <span> · Extrait de live</span>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" size="sm" onClick={handleShare}>
            Partager
          </Button>
          {channelSlug !== null && (
            <Button variant="ghost" size="sm" onClick={() => navigate(`/channel/${channelSlug}`)}>
              Voir la chaîne
            </Button>
          )}
        </div>
      </div>
      <Card>
        <dl className="space-y-1 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-text-secondary">Lien de partage</dt>
            <dd className="font-mono text-xs text-text-primary">
              /c/{clip.shareSlug}
            </dd>
          </div>
          {clip.videoId !== null && (
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">VOD source</dt>
              <dd>
                <button
                  type="button"
                  className="text-sm text-primary-400 hover:underline"
                  onClick={() => navigate(`/video/${clip.videoId}`)}
                >
                  Voir la vidéo complète
                </button>
              </dd>
            </div>
          )}
        </dl>
      </Card>
    </div>
  )
}
