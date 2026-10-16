import { useRef, useState } from 'react'
import {
  Play,
  Pause,
  SpeakerHigh,
  SpeakerSlash,
  ArrowsOut,
  GearSix,
} from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import type { FlickQuality } from '../../types/flick'

const QUALITIES: FlickQuality[] = ['Auto', '1080p', '720p', '480p', '360p', '240p']

/**
 * Lecteur vidéo FLICK 16:9 — commandes personnalisées
 * Play/Pause, volume, qualité, plein écran + halo cyan.
 */
export function FlickPlayer({ artwork = '🎬', title = 'Live FLICK' }: { artwork?: string; title?: string }) {
  const boxRef = useRef<HTMLDivElement>(null)
  const [playing, setPlaying] = useState(false)
  const [muted, setMuted] = useState(false)
  const [volume, setVolume] = useState(80)
  const [quality, setQuality] = useState<FlickQuality>('Auto')
  const [showQuality, setShowQuality] = useState(false)

  function toggleFullscreen() {
    const el = boxRef.current
    if (!el) return
    if (document.fullscreenElement) {
      void document.exitFullscreen()
    } else {
      void el.requestFullscreen?.()
    }
  }

  return (
    <div
      ref={boxRef}
      className="group relative aspect-video w-full overflow-hidden rounded-2xl border border-[#1E293B] bg-black"
      aria-label={`Lecteur : ${title}`}
    >
      {/* Scène cinématique (flux réel branché ici via HLS/RTMP) */}
      <div
        aria-hidden="true"
        className="absolute inset-0 flex items-center justify-center text-8xl"
        style={{ background: 'radial-gradient(ellipse at center, #16202E 0%, #05070A 75%)' }}
      >
        {artwork}
      </div>
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/30" />

      {/* Bouton central */}
      <button
        type="button"
        onClick={() => setPlaying((v) => !v)}
        aria-label={playing ? 'Pause' : 'Lecture'}
        className={cn(
          'absolute inset-0 m-auto flex h-20 w-20 items-center justify-center rounded-full bg-[#00F0FF] text-black shadow-[0_0_40px_rgb(0_240_255/0.5)] transition-all hover:scale-105',
          playing && 'opacity-0 group-hover:opacity-100',
        )}
      >
        {playing ? <Pause size={34} weight="fill" /> : <Play size={34} weight="fill" className="ml-1" />}
      </button>

      {/* Barre de contrôles */}
      <div className="absolute inset-x-0 bottom-0 flex items-center gap-1.5 bg-gradient-to-t from-black/90 to-transparent px-3 pt-8 pb-3">
        <button
          type="button"
          onClick={() => setPlaying((v) => !v)}
          aria-label={playing ? 'Pause' : 'Lecture'}
          className="flex h-9 w-9 items-center justify-center rounded-lg text-white hover:bg-white/10"
        >
          {playing ? <Pause size={20} weight="fill" /> : <Play size={20} weight="fill" />}
        </button>
        <button
          type="button"
          onClick={() => setMuted((v) => !v)}
          aria-label={muted ? 'Activer le son' : 'Couper le son'}
          className="flex h-9 w-9 items-center justify-center rounded-lg text-white hover:bg-white/10"
        >
          {muted || volume === 0 ? <SpeakerSlash size={20} /> : <SpeakerHigh size={20} />}
        </button>
        <input
          type="range"
          min={0}
          max={100}
          value={muted ? 0 : volume}
          onChange={(e) => {
            setVolume(Number(e.target.value))
            setMuted(false)
          }}
          aria-label="Volume"
          className="h-1 w-20 cursor-pointer accent-[#00F0FF]"
        />
        <span className="ml-1 hidden text-xs font-semibold text-white/70 sm:inline">DIRECT</span>
        <span className="ml-1 flex items-center gap-1.5 rounded bg-[#EF4444] px-1.5 py-0.5 text-[10px] font-bold text-white">
          <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-white" />
          LIVE
        </span>
        <div className="flex-1" />
        {/* Qualité */}
        <div className="relative">
          <button
            type="button"
            onClick={() => setShowQuality((v) => !v)}
            aria-haspopup="listbox"
            aria-expanded={showQuality}
            aria-label={`Qualité : ${quality}`}
            className="flex h-9 items-center gap-1.5 rounded-lg px-2.5 text-xs font-bold text-white hover:bg-white/10"
          >
            <GearSix size={18} />
            {quality}
          </button>
          {showQuality && (
            <>
              <button type="button" aria-label="Fermer" onClick={() => setShowQuality(false)} className="fixed inset-0 z-10 cursor-default" />
              <div role="listbox" aria-label="Choisir la qualité" className="absolute right-0 bottom-11 z-20 w-32 overflow-hidden rounded-xl border border-[#1E293B] bg-[#111923] p-1 shadow-2xl">
                {QUALITIES.map((q) => (
                  <button
                    key={q}
                    type="button"
                    role="option"
                    aria-selected={q === quality}
                    onClick={() => {
                      setQuality(q)
                      setShowQuality(false)
                    }}
                    className={cn(
                      'block w-full rounded-lg px-3 py-1.5 text-left text-xs font-semibold',
                      q === quality ? 'bg-[#00F0FF]/15 text-[#00F0FF]' : 'text-[#94A3B8] hover:bg-white/5 hover:text-white',
                    )}
                  >
                    {q}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
        <button
          type="button"
          onClick={toggleFullscreen}
          aria-label="Plein écran"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-white hover:bg-white/10"
        >
          <ArrowsOut size={20} />
        </button>
      </div>
    </div>
  )
}
