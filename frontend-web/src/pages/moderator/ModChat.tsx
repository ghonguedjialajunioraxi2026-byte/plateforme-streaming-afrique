import { useEffect, useState, type FormEvent } from 'react'
import { Badge, Button, Card, Input, Loader, useToast } from '../../components/ui'
import { ConfirmModal } from '../../components/admin/ConfirmModal'
import { formatDate } from '../../utils/format'
import { moderationApi, type ChatMessage } from '../../services/api/moderation'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Modération du chat (étape 17) : /moderation/chat.
/// Historique d'un live (id saisi) + masquage motivé (message conservé
/// pour dossier, acte audité). Timeouts et bannissements : sanctions sur
/// signalement (/moderation/reports) — pas de second système.
export default function ModChat() {
  usePageMeta('Chat', 'Modération du chat en direct')
  const toast = useToast()
  const [streamId, setStreamId] = useState('')
  const [appliedStream, setAppliedStream] = useState('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hiding, setHiding] = useState<ChatMessage | null>(null)
  const [acting, setActing] = useState(false)

  useEffect(() => {
    if (appliedStream === '') return
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        const result = await moderationApi.chat(Number(appliedStream), 0, 50)
        if (!cancelled) {
          setMessages(result.content)
          setError(null)
        }
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
  }, [appliedStream])

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (streamId.trim() === '') return
    setAppliedStream(streamId.trim())
  }

  async function handleHide(reason: string) {
    if (hiding === null) return
    setActing(true)
    try {
      const updated = await moderationApi.hideMessage(hiding.id, reason)
      setMessages((current) => current.map((m) => (m.id === updated.id ? updated : m)))
      setHiding(null)
      toast.show('Message masqué — acte audité', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Action impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Modération du chat</h1>

      <Card>
        <form onSubmit={handleSubmit} className="flex flex-wrap items-end gap-2" noValidate>
          <div className="min-w-[160px] flex-1">
            <Input
              label="Direct (id)"
              name="modchat-stream"
              type="number"
              min={1}
              value={streamId}
              onChange={(e) => setStreamId(e.target.value)}
              placeholder="ex. 2"
            />
          </div>
          <Button type="submit" variant="secondary" size="sm">Charger l’historique</Button>
        </form>
      </Card>

      {appliedStream !== '' && (
        <>
          {loading ? (
            <div className="flex min-h-[20vh] items-center justify-center" role="status" aria-label="Chargement">
              <Loader size="lg" />
            </div>
          ) : error !== null ? (
            <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
          ) : messages.length === 0 ? (
            <Card><p className="text-sm text-text-secondary">Aucun message sur ce live.</p></Card>
          ) : (
            <div className="space-y-2">
              {messages.map((message) => (
                <Card key={message.id}>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0 flex-1">
                      <p className="flex flex-wrap items-center gap-2 text-sm">
                        <span className="font-semibold text-text-primary">{message.authorName}</span>
                        {message.system && <Badge variant="neutral">Système</Badge>}
                        {message.status === 'HIDDEN'
                          ? <Badge variant="danger">Masqué</Badge>
                          : <Badge variant="accent">Visible</Badge>}
                      </p>
                      <p className="mt-1 text-sm text-text-secondary">{message.bodyDisplay}</p>
                      <p className="mt-1 text-xs text-text-muted">
                        #{message.id} · {formatDate(message.createdAt)}
                        {message.hiddenReason !== null && ` · motif : ${message.hiddenReason}`}
                      </p>
                    </div>
                    {message.status !== 'HIDDEN' && !message.system && (
                      <Button variant="ghost" size="sm" onClick={() => setHiding(message)}>
                        Masquer
                      </Button>
                    )}
                  </div>
                </Card>
              ))}
            </div>
          )}
        </>
      )}

      <ConfirmModal
        open={hiding !== null}
        title={`Masquer le message #${hiding?.id ?? ''} ?`}
        message="Le message restera conservé pour dossier (statut HIDDEN). Acte audité."
        confirmLabel="Masquer"
        danger
        requireReason
        loading={acting}
        onClose={() => setHiding(null)}
        onConfirm={handleHide}
      />
    </div>
  )
}
