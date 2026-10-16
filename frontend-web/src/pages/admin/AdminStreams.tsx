import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { ConfirmModal } from '../../components/admin/ConfirmModal'
import { formatCount, formatDate } from '../../utils/format'
import { streamsApi, type StreamDetail } from '../../services/api/streams'
import { adminApi } from '../../services/api/admin'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Surveillance des lives (étape 17) : /admin/streams.
/// Directs en cours (viewers temps réel) + arrêt administratif motivé
/// (VOD auto + acte audité STREAM_STOPPED). Lecture seule sinon.
export default function AdminStreams() {
  usePageMeta('Lives', 'Surveillance des directs en cours')
  const toast = useToast()
  const [live, setLive] = useState<StreamDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [stopping, setStopping] = useState<StreamDetail | null>(null)
  const [acting, setActing] = useState(false)

  const reload = useCallback(async () => {
    const page = await streamsApi.listLive(0, 50)
    setLive(page.content)
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

  async function handleStop() {
    if (stopping === null) return
    setActing(true)
    try {
      await adminApi.stopStream(stopping.id)
      setLive((current) => current.filter((s) => s.id !== stopping.id))
      setStopping(null)
      toast.show('Direct arrêté — VOD en préparation, acte audité', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Arrêt impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Lives en cours</h1>

      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : live.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucun direct en cours sur la plateforme.</p></Card>
      ) : (
        <div className="space-y-2">
          {live.map((stream) => (
            <Card key={stream.id}>
              <div className="flex flex-wrap items-center gap-3">
                <div className="min-w-0 flex-1">
                  <p className="flex flex-wrap items-center gap-2 font-semibold text-text-primary">
                    <Badge variant="live" dot>LIVE</Badge>
                    <Link to={`/live/${stream.id}`} className="truncate hover:underline">{stream.title}</Link>
                  </p>
                  <p className="mt-1 text-xs text-text-muted">
                    Chaîne #{stream.creatorId}
                    {stream.categoryName !== null && ` · ${stream.categoryName}`}
                    {' · '}{formatCount(stream.viewerCount)} spectateurs · pic {formatCount(stream.peakViewers)}
                    {stream.startedAt !== null && ` · depuis ${formatDate(stream.startedAt)}`}
                  </p>
                </div>
                <Button variant="danger" size="sm" onClick={() => setStopping(stream)}>
                  Arrêter
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <ConfirmModal
        open={stopping !== null}
        title={`Arrêter « ${stopping?.title ?? ''} » ?`}
        message="Le direct passera ENDED et une VOD sera préparée automatiquement. Acte audité STREAM_STOPPED."
        confirmLabel="Arrêter le direct"
        danger
        loading={acting}
        onClose={() => setStopping(null)}
        onConfirm={() => handleStop()}
      />
    </div>
  )
}
