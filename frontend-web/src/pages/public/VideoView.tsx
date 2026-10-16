import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router'
import { VideoPlayer } from '../../components/streaming/VideoPlayer'
import { ShareModal } from '../../components/streaming'
import { Badge, Button, Card, EmptyState, Input, Modal, useToast } from '../../components/ui'
import { Icon } from '../../components/ui/icons'
import { cn } from '../../utils/cn'
import { mediaUrl } from '../../utils/media'
import { formatCount, formatDate, formatDuration } from '../../utils/format'
import { useAuth } from '../../features/auth/useAuth'
import { creatorsApi } from '../../services/api/creators'
import { videosApi, type Playback, type Video } from '../../services/api/videos'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Page VOD (étape 15) : /video/:id.
/// Lecteur + métadonnées + vue comptée une fois + découpage de clip.
export default function VideoView() {
  const { id = '' } = useParams<{ id: string }>()
  const { isAuthenticated } = useAuth()
  const toast = useToast()
  const navigate = useNavigate()
  const videoId = Number(id)

  const [video, setVideo] = useState<Video | null>(null)
  const [playback, setPlayback] = useState<Playback | null>(null)
  const [views, setViews] = useState<number | null>(null)
  const [status, setStatus] = useState<'loading' | 'ready' | 'not-found' | 'error'>('loading')
  const [error, setError] = useState<string | null>(null)
  const [clipOpen, setClipOpen] = useState(false)
  const [shareOpen, setShareOpen] = useState(false)
  const [liked, setLiked] = useState(false)
  const [channelSlug, setChannelSlug] = useState<string | null>(null)
  const viewSent = useRef(false)
  usePageMeta(
    video?.title ?? null,
    video?.description ?? (video !== null ? `Regarder ${video.title} en replay` : null),
  )

  useEffect(() => {
    let cancelled = false
    async function load() {
      setStatus('loading')
      viewSent.current = false
      try {
        const detail = await videosApi.get(videoId)
        if (cancelled) return
        setVideo(detail)
        setViews(detail.viewCount)
        setStatus('ready')
        creatorsApi.getById(detail.creatorId).then(
          (channel) => {
            if (!cancelled) setChannelSlug(channel.slug)
          },
          () => undefined,
        )
        try {
          const urls = await videosApi.playback(videoId)
          if (!cancelled) setPlayback(urls)
        } catch {
          // Playback indisponible (en traitement) : la page reste lisible.
        }
        if (!viewSent.current) {
          viewSent.current = true
          try {
            const count = await videosApi.recordView(videoId)
            if (!cancelled) setViews(count)
          } catch {
            // Vue non comptée (privée, anti-abus) : silencieux.
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
    if (Number.isFinite(videoId)) {
      void load()
    } else {
      setStatus('not-found')
    }
    return () => {
      cancelled = true
    }
  }, [videoId])

  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-5xl space-y-4" aria-busy="true" aria-label="Chargement de la vidéo">
        <div className="aspect-video w-full animate-pulse rounded-xl bg-bg-elevated" />
        <div className="h-6 w-2/3 animate-pulse rounded bg-bg-elevated" />
      </div>
    )
  }

  if (status === 'not-found') {
    return (
      <div className="mx-auto max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">404 — Vidéo introuvable</p>
          <p className="mt-1 text-sm text-text-secondary">
            Cette vidéo n'existe pas, plus, ou n'est pas visible (privée, en traitement, supprimée).
          </p>
        </Card>
      </div>
    )
  }

  if (status === 'error' || video === null) {
    return (
      <div className="mx-auto max-w-3xl" role="alert">
        <Card>
          <p className="font-display text-xl font-bold text-text-primary">Vidéo inaccessible</p>
          <p className="mt-1 text-sm text-text-secondary">{error ?? 'Erreur inconnue.'}</p>
        </Card>
      </div>
    )
  }

  const playable = playback?.playbackUrl ?? video.videoUrl
  const poster = video.thumbnailUrl !== null ? (mediaUrl(video.thumbnailUrl) ?? undefined) : undefined

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <VideoPlayer
        src={playable ?? undefined}
        poster={poster}
      />
      {video.processingStatus !== 'READY' && (
        <Card className="border-warning/40">
          <p className="text-sm text-text-primary" role="status">
            {video.processingStatus === 'PROCESSING'
              ? 'Vidéo en cours de traitement — lecture possible dès sa disponibilité.'
              : 'Traitement en échec — le fichier est indisponible.'}
          </p>
        </Card>
      )}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="font-display text-xl font-bold text-text-primary">{video.title}</h1>
          <p className="mt-1 text-sm text-text-secondary">
            {views !== null ? `${formatCount(views)} vues` : '…'} · {formatDate(video.createdAt)}
            {' · '}{formatDuration(video.durationSeconds)}
            {video.originStreamId !== null && <span> · Replay de live</span>}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {video.visibility !== 'PUBLIC' && (
            <Badge variant="warning">{video.visibility === 'PRIVATE' ? 'Privée' : 'Non répertoriée'}</Badge>
          )}
          {isAuthenticated && video.processingStatus === 'READY' && (
            <Button variant="secondary" size="sm" onClick={() => setClipOpen(true)}>
              ✂ Créer un clip
            </Button>
          )}
          {channelSlug !== null && (
            <Button variant="ghost" size="sm" onClick={() => navigate(`/channel/${channelSlug}`)}>
              Voir la chaîne
            </Button>
          )}
        </div>
      </div>
      {video.description && (
        <Card>
          <p className="whitespace-pre-wrap text-sm text-text-secondary">{video.description}</p>
        </Card>
      )}
      {/* Actions mobiles maquette (écran 18) : J'aime + Partager */}
      <div className="flex items-center gap-2 md:hidden">
        <button
          type="button"
          onClick={() => setLiked((value) => !value)}
          aria-pressed={liked}
          aria-label={liked ? 'Ne plus aimer cette vidéo' : 'Aimer cette vidéo'}
          className={cn(
            'flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border text-sm font-semibold transition-colors duration-200 active:scale-[0.98]',
            liked
              ? 'border-danger/60 bg-danger/10 text-danger'
              : 'border-border-default bg-bg-elevated text-text-secondary active:text-text-primary',
          )}
        >
          <Icon name="heart" className={cn('h-5 w-5', liked && 'fill-current')} />
          {liked ? 'Aimé' : "J'aime"}
        </button>
        <button
          type="button"
          onClick={() => setShareOpen(true)}
          className="flex h-11 flex-1 items-center justify-center gap-1.5 rounded-xl border border-border-default bg-bg-elevated text-sm font-semibold text-text-secondary transition-colors active:text-text-primary"
        >
          <Icon name="share" className="h-5 w-5" />
          Partager
        </button>
      </div>
      {/* Commentaires mobiles — le backend n'expose pas encore de
          commentaires VOD : état vide honnête, jamais de faux avis. */}
      <section aria-label="Commentaires" className="md:hidden">
        <h2 className="mb-2 font-display text-[17px] font-bold tracking-tight text-text-primary">
          Commentaires
        </h2>
        <EmptyState
          icon="chat"
          title="Aucun commentaire"
          description="Sois le premier à réagir dès que les commentaires seront disponibles."
        />
      </section>
      <ClipCreatorModal
        open={clipOpen}
        onClose={() => setClipOpen(false)}
        videoId={video.id}
        videoTitle={video.title}
        videoDuration={video.durationSeconds}
        onCreated={(clipId) => {
          toast.show('Clip en cours de préparation', 'success')
          setClipOpen(false)
          window.location.assign(`/clip/${clipId}`)
        }}
      />
      <ShareModal
        open={shareOpen}
        onClose={() => setShareOpen(false)}
        title={video.title}
        channelName={channelSlug ?? `Chaîne #${video.creatorId}`}
      />
    </div>
  )
}

/// Modale de découpage (étape 15, §67) : titre + début + fin, durée affichée.
/// Le serveur revalide tout (bornes, durée max).
export function ClipCreatorModal({
  open,
  onClose,
  videoId,
  videoTitle,
  videoDuration,
  onCreated,
}: {
  open: boolean
  onClose: () => void
  videoId: number
  videoTitle: string
  videoDuration: number
  onCreated: (clipId: number) => void
}) {
  const [title, setTitle] = useState('')
  const [start, setStart] = useState('0')
  const [end, setEnd] = useState('25')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const toast = useToast()

  const startSeconds = Math.max(0, Number.parseInt(start, 10) || 0)
  const endSeconds = Math.max(0, Number.parseInt(end, 10) || 0)
  const duration = Math.max(0, endSeconds - startSeconds)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (title.trim().length === 0) {
      setFormError('Le titre est obligatoire')
      return
    }
    if (duration <= 0) {
      setFormError('La fin doit être après le début')
      return
    }
    setFormError(null)
    setSubmitting(true)
    try {
      const clip = await videosApi.createClip(videoId, {
        title: title.trim(),
        startTimeSeconds: startSeconds,
        durationSeconds: duration,
      })
      onCreated(clip.id)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Création impossible'
      setFormError(message)
      toast.show(message, 'error')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={`Clip — ${videoTitle}`}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <Input
          label="Titre du clip"
          name="clip-title"
          placeholder="Moment incroyable"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={150}
        />
        <div className="grid grid-cols-2 gap-3">
          <Input
            label="Début (secondes)"
            name="clip-start"
            type="number"
            min={0}
            max={videoDuration}
            value={start}
            onChange={(e) => setStart(e.target.value)}
          />
          <Input
            label="Fin (secondes)"
            name="clip-end"
            type="number"
            min={0}
            max={videoDuration}
            value={end}
            onChange={(e) => setEnd(e.target.value)}
          />
        </div>
        <p className="text-sm text-text-secondary" aria-live="polite">
          Durée : {formatDuration(duration)} (extrait de {formatDuration(videoDuration)} max, 60 s par défaut)
        </p>
        {formError !== null && (
          <p className="text-sm text-danger" role="alert">{formError}</p>
        )}
        <Button type="submit" variant="primary" fullWidth loading={submitting} disabled={submitting}>
          Créer le clip
        </Button>
      </form>
    </Modal>
  )
}
