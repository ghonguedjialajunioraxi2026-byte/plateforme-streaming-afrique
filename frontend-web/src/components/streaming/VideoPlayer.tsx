import { useEffect, useRef, useState } from 'react'
import { cn } from '../../utils/cn'
import { useI18n } from '../../i18n'
import { useDataSaver } from '../../features/region/DataSaverContext'

export interface VideoPlayerProps {
  /** URL de la vidéo (MP4/WebM direct ; HLS via `hlsUrl` quand servi). */
  src?: string
  /** URL HLS (`.m3u8`, lives SRS) — prioritaire si `src` n'est pas HLS. */
  hlsUrl?: string | null
  /** Affiche une vignette plutôt que la vidéo (avant le clic "play"). */
  poster?: string
  className?: string
  /** Lecture automatique (muette si exigée par le navigateur). */
  autoPlay?: boolean
  /** Position de départ (clips : extrait dans la source). */
  startAtSeconds?: number
  /** Remontée du temps courant (éditeur de clip). */
  onTimeUpdate?: (seconds: number) => void
  /** Remontée de la durée totale dès les métadonnées. */
  onLoadedMetadata?: (durationSeconds: number) => void
}

type PlayerState = 'waiting' | 'loading' | 'playing' | 'buffering' | 'reconnecting' | 'failed'

const QUALITIES = ['144p', '240p', '360p', '480p', '720p', '1080p'] as const

/**
 * Lecteur vidéo (étapes 15 + 19 : HTML5 natif + états + data saver).
 *
 * MP4/WebM : lecture directe. HLS (lives SRS, `hlsUrl` ou `.m3u8`) :
 * natif sur Safari, `hls.js` ailleurs (import dynamique, reconnexion 3x).
 * Économie de données : pas de préchargement, pas d'autoplay, qualité
 * préférée 240p. Le menu de qualité reste indicatif sur source unique :
 * `Auto` lit la source telle quelle (aucune fausse promesse).
 * Reconnexion : 3 tentatives espacées avant l'erreur fatale.
 */
export function VideoPlayer({
  src,
  hlsUrl,
  poster,
  className,
  autoPlay = false,
  startAtSeconds = 0,
  onTimeUpdate,
  onLoadedMetadata,
}: VideoPlayerProps) {
  const { t } = useI18n()
  const { enabled: dataSaver } = useDataSaver()
  const videoRef = useRef<HTMLVideoElement>(null)
  const hlsRef = useRef<{ destroy: () => void } | null>(null)
  const hlsSrc = hlsUrl ?? (src && /\.m3u8(\?|$)/i.test(src) ? src : null)
  const progressiveSrc = hlsSrc ? undefined : src
  const [state, setState] = useState<PlayerState>(src || hlsSrc ? 'loading' : 'waiting')
  const [quality, setQuality] = useState<string>(() => {
    try {
      return localStorage.getItem('flick-quality') ?? 'Auto'
    } catch {
      return 'Auto'
    }
  })
  const [attempts, setAttempts] = useState(0)
  const startedRef = useRef(false)

  // Le data saver impose 240p par défaut (choix manuel conservé ensuite).
  useEffect(() => {
    if (dataSaver) {
      setQuality((current) => (current === 'Auto' ? '240p' : current))
    }
  }, [dataSaver])

  // Nouvelle source → on repart en chargement.
  useEffect(() => {
    setState(src || hlsSrc ? 'loading' : 'waiting')
    setAttempts(0)
    startedRef.current = false
  }, [src, hlsSrc])

  // HLS : natif (Safari) ou hls.js (Chrome/Firefox/Edge).
  useEffect(() => {
    const video = videoRef.current
    if (!video || !hlsSrc) return
    let cancelled = false
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = hlsSrc
      return () => {
        cancelled = true
      }
    }
    void import('hls.js')
      .then(({ default: Hls }) => {
        if (cancelled || !videoRef.current) return
        if (!Hls.isSupported()) {
          setState('failed')
          return
        }
        hlsRef.current?.destroy()
        const hls = new Hls({ maxBufferLength: 30, liveSyncDurationCount: 3 })
        hlsRef.current = hls
        hls.on(Hls.Events.ERROR, (_, data) => {
          if (data.fatal) handleError()
        })
        hls.loadSource(hlsSrc)
        hls.attachMedia(videoRef.current)
      })
      .catch(() => {
        if (!cancelled) setState('failed')
      })
    return () => {
      cancelled = true
      hlsRef.current?.destroy()
      hlsRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hlsSrc])

  function handleLoadedMetadata() {
    const video = videoRef.current
    if (video === null) return
    if (!startedRef.current && startAtSeconds > 0 && Number.isFinite(video.duration)) {
      startedRef.current = true
      video.currentTime = Math.min(startAtSeconds, Math.max(0, video.duration - 1))
    }
    if (Number.isFinite(video.duration)) {
      onLoadedMetadata?.(Math.floor(video.duration))
    }
  }

  function retry() {
    const video = videoRef.current
    if (video === null || (!progressiveSrc && !hlsSrc)) return
    setAttempts((count) => count + 1)
    setState('reconnecting')
    // Backoff simple : la tentative suit après un court délai.
    window.setTimeout(() => {
      video.load()
      void video.play().catch(() => setState('failed'))
    }, Math.min(1000 * 2 ** attempts, 8000))
  }

  function handleError() {
    if (attempts >= 3) {
      setState('failed')
      return
    }
    retry()
  }

  function changeQuality(next: string) {
    setQuality(next)
    try {
      localStorage.setItem('flick-quality', next)
    } catch {
      // Préférence non persistée : reste en mémoire.
    }
  }

  const overlay = state === 'buffering' || state === 'reconnecting' || state === 'loading'
  const hasSource = Boolean(progressiveSrc ?? hlsSrc)

  if (state === 'failed') {
    return (
      <div
        className={cn(
          'relative flex aspect-video w-full flex-col items-center justify-center gap-2 overflow-hidden rounded-xl bg-black text-text-muted',
          className,
        )}
        role="alert"
      >
        <span className="text-5xl">⚠️</span>
        <p className="text-sm">{t('player.unavailable')}</p>
        {hasSource && (
          <button
            type="button"
            onClick={retry}
            className="rounded-lg bg-primary-500 px-4 py-2 text-sm font-medium text-text-inverse"
          >
            {t('player.retry')}
          </button>
        )}
      </div>
    )
  }

  return (
    <div
      className={cn(
        'relative aspect-video w-full overflow-hidden rounded-xl bg-black',
        className,
      )}
    >
      {progressiveSrc || hlsSrc ? (
        <video
          ref={videoRef}
          src={progressiveSrc}
          poster={poster}
          controls
          playsInline
          autoPlay={autoPlay && !dataSaver}
          preload={dataSaver ? 'none' : 'metadata'}
          onLoadedMetadata={handleLoadedMetadata}
          onTimeUpdate={(event) => onTimeUpdate?.(Math.floor(event.currentTarget.currentTime))}
          onCanPlay={() => setState('playing')}
          onWaiting={() => setState('buffering')}
          onPlaying={() => setState('playing')}
          onStalled={() => setState('buffering')}
          onError={handleError}
          className="h-full w-full object-contain"
        />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-2 text-text-muted">
          <span className="text-5xl">📡</span>
          <p className="text-sm">{t('player.waiting')}</p>
        </div>
      )}

      {overlay && hasSource && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-black/40" role="status">
          <p className="rounded-lg bg-black/70 px-4 py-2 text-sm text-white">
            {state === 'reconnecting' ? t('player.reconnecting') : t('common.loading')}
          </p>
        </div>
      )}

      {hasSource && (
        <div className="absolute right-2 top-2 flex items-center gap-2">
          {dataSaver && (
            <span className="rounded bg-black/70 px-2 py-1 text-xs text-white" title={t('player.dataSaverOn')}>
              ⏳
            </span>
          )}
          <label className="sr-only" htmlFor="player-quality">
            {t('player.quality')}
          </label>
          <select
            id="player-quality"
            value={quality}
            onChange={(event) => changeQuality(event.target.value)}
            className="rounded bg-black/70 px-2 py-1 text-xs text-white"
            title={t('player.quality')}
          >
            <option value="Auto">{t('player.auto')}</option>
            {QUALITIES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}
