import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { GearSix, Smiley, PaperPlaneTilt } from '@phosphor-icons/react'
import { cn } from '../../utils/cn'
import type { FlickChatMessage } from '../../types/flick'

interface FlickChatProps {
  messages: FlickChatMessage[]
  connected?: boolean
  className?: string
}

/**
 * Panneau latéral droit — Chat live FLICK, 320px.
 * En-tête + flux temps réel + message système + saisie.
 */
export function FlickChat({ messages, connected = true, className }: FlickChatProps) {
  const [draft, setDraft] = useState('')
  const [local, setLocal] = useState<FlickChatMessage[]>([])
  const [stuck, setStuck] = useState(true)
  const listRef = useRef<HTMLDivElement>(null)

  const all = [...messages, ...local]

  function scrollBottom(smooth = true) {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: smooth ? 'smooth' : 'auto' })
  }

  useEffect(() => {
    if (stuck) scrollBottom(false)
  }, [all.length, stuck])

  function onScroll() {
    const el = listRef.current
    if (!el) return
    setStuck(el.scrollHeight - el.scrollTop - el.clientHeight < 48)
  }

  function send(event: FormEvent) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || !connected) return
    setLocal((prev) => [
      ...prev,
      { id: `local-${Date.now()}`, username: 'Toi', color: 'var(--color-primary-500)', message: text },
    ])
    setDraft('')
    setStuck(true)
  }

  return (
    <div className={cn('flex h-full min-h-0 flex-col overflow-hidden rounded-xl border border-border-default bg-bg-surface', className)}>
      {/* En-tête */}
      <div className="flex items-center justify-between border-b border-border-default px-4 py-3">
        <h2 className="text-sm font-bold tracking-widest text-text-primary uppercase">Chat du live</h2>
        <button
          type="button"
          aria-label="Options de modération"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-text-secondary hover:bg-bg-hover hover:text-text-primary"
        >
          <GearSix size={18} />
        </button>
      </div>

      {/* Flux */}
      <div className="relative min-h-0 flex-1">
        <div ref={listRef} onScroll={onScroll} aria-live="polite" aria-label="Messages du chat" className="h-full space-y-0.5 overflow-y-auto p-3.5">
          <p className="mb-2 rounded-lg border border-primary-500/20 bg-primary-500/5 px-3 py-2.5 text-[13px] leading-relaxed text-text-secondary">
            <span className="font-bold text-primary-500">Bienvenue 👋 </span>
            Respecte la communauté : pas de spam, pas de haine. Les modérateurs veillent.
          </p>
          {all.map((msg) =>
            msg.isSystem ? (
              <p key={msg.id} className="rounded-lg bg-bg-elevated px-2.5 py-1.5 text-[13px] text-text-secondary">
                <span className="font-bold" style={{ color: msg.color }}>{msg.username}</span>
                <span className="text-text-muted"> — </span>{msg.message}
              </p>
            ) : (
              <p key={msg.id} className="animate-rise rounded px-1.5 py-1 text-sm leading-relaxed break-words hover:bg-bg-elevated">
                {msg.badge && (
                  <span className={cn(
                    'mr-1.5 inline-block rounded px-1.5 py-px align-middle text-[10px] font-bold tracking-wide uppercase',
                    msg.badge === 'Diffuseur' && 'bg-primary-500 text-black',
                    msg.badge === 'Modo' && 'bg-emerald-500/20 text-emerald-600',
                    msg.badge === 'VIP' && 'bg-amber-500/20 text-amber-600',
                    (msg.badge === 'Abonné' || msg.badge === 'Fondateur') && 'bg-danger/15 text-danger',
                  )}>
                    {msg.badge}
                  </span>
                )}
                <span className="font-bold" style={{ color: msg.color }}>{msg.username}</span>
                <span className="text-text-muted">: </span>
                <span className="text-text-primary">{msg.message}</span>
              </p>
            ),
          )}
        </div>
        {!stuck && (
          <button
            type="button"
            onClick={() => scrollBottom()}
            className="absolute inset-x-4 bottom-3 rounded-lg bg-primary-500 py-2 text-sm font-bold text-black shadow-lg hover:bg-primary-300"
          >
            Reprendre le direct ↓
          </button>
        )}
      </div>

      {/* Saisie */}
      <form onSubmit={send} className="border-t border-border-default p-3">
        <div className="flex items-center gap-2">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder={connected ? 'Écrire un message...' : 'Connecte-toi pour chater'}
            disabled={!connected}
            aria-label="Écrire un message dans le chat"
            className="h-10 min-w-0 flex-1 rounded-full border border-border-default bg-bg-elevated px-4 text-sm text-text-primary placeholder:text-text-muted focus:border-primary-500 focus:outline-none disabled:opacity-50"
          />
          <button
            type="button"
            aria-label="Ajouter un emoji"
            onClick={() => setDraft((d) => `${d}🔥`)}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-text-secondary hover:bg-bg-hover hover:text-text-primary"
          >
            <Smiley size={20} />
          </button>
          <button
            type="submit"
            disabled={!connected || draft.trim().length === 0}
            aria-label="Envoyer le message"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-500 text-black hover:bg-primary-300 disabled:cursor-not-allowed disabled:opacity-40"
          >
            <PaperPlaneTilt size={18} weight="fill" />
          </button>
        </div>
      </form>
    </div>
  )
}
