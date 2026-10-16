import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { formatCount, formatDate, formatDuration } from '../../utils/format'
import { clipsApi } from '../../services/api/clips'
import type { Clip } from '../../services/api/videos'

/// Modération clips (étape 15, §57) — ADMIN : recherche, statut, suppression.
export default function AdminClips() {
  const toast = useToast()
  const [clips, setClips] = useState<Clip[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (next: number) => {
    const result = await clipsApi.adminAll(next, 20)
    setClips(result.content)
    setTotal(result.totalElements)
    setPage(result.page)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function init() {
      try {
        await load(0)
        if (!cancelled) setError(null)
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Chargement impossible')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void init()
    return () => {
      cancelled = true
    }
  }, [load])

  async function remove(clip: Clip) {
    if (!window.confirm(`Masquer « ${clip.title} » ?`)) return
    try {
      await clipsApi.remove(clip.id)
      setClips((current) => current.filter((c) => c.id !== clip.id))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Suppression impossible')
    }
  }

  async function restore(clip: Clip) {
    try {
      const updated = await clipsApi.restore(clip.id)
      setClips((current) => current.map((c) => (c.id === clip.id ? updated : c)))
      toast.show('Clip restauré', 'success')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Restauration impossible')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Gestion des clips</h1>
      <p className="text-sm text-text-secondary">
        Clips de la communauté — auteur du découpage et chaîne source tracés pour la modération.
      </p>
      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement des clips">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
        </Card>
      ) : clips.length === 0 ? (
        <Card>
          <p className="text-sm text-text-secondary">Aucun clip enregistré.</p>
        </Card>
      ) : (
        <>
          <Card noPadding>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border-default text-xs uppercase text-text-muted">
                    <th scope="col" className="px-4 py-3">Titre</th>
                    <th scope="col" className="px-4 py-3">Source</th>
                    <th scope="col" className="px-4 py-3">Durée</th>
                    <th scope="col" className="px-4 py-3">Vues</th>
                    <th scope="col" className="px-4 py-3">Statut</th>
                    <th scope="col" className="px-4 py-3">Date</th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Masquer</span></th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Restaurer</span></th>
                  </tr>
                </thead>
                <tbody>
                  {clips.map((clip) => (
                    <tr key={clip.id} className="border-b border-border-default last:border-0">
                      <td className="max-w-64 truncate px-4 py-3 font-medium text-text-primary">
                        {clip.title}
                        {clip.deleted && (
                          <span className="ml-2">
                            <Badge variant="danger">Masqué</Badge>
                          </span>
                        )}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-xs text-text-secondary">
                        {clip.videoId !== null ? `VOD #${clip.videoId}` : `Live #${clip.streamId}`}
                        {clip.createdByUserId !== null && ` · par #${clip.createdByUserId}`}
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {formatDuration(clip.durationSeconds)}
                      </td>
                      <td className="px-4 py-3 text-text-secondary">{formatCount(clip.viewCount)}</td>
                      <td className="px-4 py-3">
                        <Badge variant={clip.processingStatus === 'READY' ? 'live' : clip.processingStatus === 'FAILED' ? 'danger' : 'warning'}>
                          {clip.processingStatus}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {formatDate(clip.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        <Button variant="ghost" size="sm" onClick={() => remove(clip)}>
                          Masquer
                        </Button>
                      </td>
                      <td className="px-4 py-3">
                        {clip.deleted ? (
                          <Button variant="secondary" size="sm" onClick={() => restore(clip)}>
                            Restaurer
                          </Button>
                        ) : (
                          <span className="text-xs text-text-muted">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
          {totalPages > 1 && (
            <nav className="flex items-center justify-between" aria-label="Pagination">
              <Button variant="secondary" size="sm" disabled={page <= 0}
                onClick={() => load(page - 1)}>
                ← Précédent
              </Button>
              <span className="text-xs text-text-muted" aria-live="polite">
                Page {page + 1} / {totalPages} · {total} clip{total > 1 ? 's' : ''}
              </span>
              <Button variant="secondary" size="sm" disabled={page + 1 >= totalPages}
                onClick={() => load(page + 1)}>
                Suivant →
              </Button>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
