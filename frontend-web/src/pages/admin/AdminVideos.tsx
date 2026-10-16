import { useCallback, useEffect, useState } from 'react'
import { Badge, Button, Card, Loader, useToast } from '../../components/ui'
import { formatCount, formatDate } from '../../utils/format'
import { videosApi, type Video } from '../../services/api/videos'

/// Supervision VOD (étape 15, §56) — ADMIN, lecture + modération.
/// Visibilité forcée, masquage logique, erreurs de traitement visibles.
export default function AdminVideos() {
  const toast = useToast()
  const [videos, setVideos] = useState<Video[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async (next: number) => {
    const result = await videosApi.adminAll(next, 20)
    setVideos(result.content)
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

  async function privatize(video: Video) {
    try {
      const updated = await videosApi.changeVisibility(video.id, 'PRIVATE')
      setVideos((current) => current.map((v) => (v.id === video.id ? updated : v)))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Modération impossible')
    }
  }

  async function restore(video: Video) {
    try {
      const updated = await videosApi.restore(video.id)
      setVideos((current) => current.map((v) => (v.id === video.id ? updated : v)))
      toast.show('Vidéo restaurée', 'success')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Restauration impossible')
    }
  }

  const totalPages = Math.max(1, Math.ceil(total / 20))

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Gestion des vidéos</h1>
      <p className="text-sm text-text-secondary">
        VOD de la plateforme — privées, en traitement et masquées incluses. Modération par
        privatisation (jamais d'effacement d'historique).
      </p>
      {loading ? (
        <div className="flex min-h-[30vh] items-center justify-center" role="status" aria-label="Chargement des vidéos">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
        </Card>
      ) : videos.length === 0 ? (
        <Card>
          <p className="text-sm text-text-secondary">Aucune vidéo enregistrée.</p>
        </Card>
      ) : (
        <>
          <Card noPadding>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border-default text-xs uppercase text-text-muted">
                    <th scope="col" className="px-4 py-3">Titre</th>
                    <th scope="col" className="px-4 py-3">Chaîne</th>
                    <th scope="col" className="px-4 py-3">Vues</th>
                    <th scope="col" className="px-4 py-3">Statut</th>
                    <th scope="col" className="px-4 py-3">Visibilité</th>
                    <th scope="col" className="px-4 py-3">Date</th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Modération</span></th>
                    <th scope="col" className="px-4 py-3"><span className="sr-only">Restauration</span></th>
                  </tr>
                </thead>
                <tbody>
                  {videos.map((video) => (
                    <tr key={video.id} className="border-b border-border-default last:border-0">
                      <td className="max-w-64 truncate px-4 py-3 font-medium text-text-primary">
                        {video.title}
                        {video.deleted && (
                          <span className="ml-2">
                            <Badge variant="danger">Masquée</Badge>
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-text-secondary">#{video.creatorId}</td>
                      <td className="px-4 py-3 text-text-secondary">{formatCount(video.viewCount)}</td>
                      <td className="px-4 py-3">
                        <Badge variant={video.processingStatus === 'READY' ? 'live' : video.processingStatus === 'FAILED' ? 'danger' : 'warning'}>
                          {video.processingStatus === 'READY' ? 'Prête' : video.processingStatus === 'FAILED' ? 'Échec' : video.processingStatus === 'PROCESSING' ? 'Traitement' : 'Reçue'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant={video.visibility === 'PUBLIC' ? 'primary' : 'neutral'}>
                          {video.visibility}
                        </Badge>
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-text-secondary">
                        {formatDate(video.createdAt)}
                      </td>
                      <td className="px-4 py-3">
                        {video.visibility !== 'PRIVATE' && (
                          <Button variant="ghost" size="sm" onClick={() => privatize(video)}>
                            Privatiser
                          </Button>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        {video.deleted ? (
                          <Button variant="secondary" size="sm" onClick={() => restore(video)}>
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
                Page {page + 1} / {totalPages} · {total} vidéo{total > 1 ? 's' : ''}
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
