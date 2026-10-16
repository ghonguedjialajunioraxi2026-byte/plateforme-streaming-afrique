import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Key, Eye, Users, TrendUp, Copy, Check } from '@phosphor-icons/react'
import { Badge, Button, Card, Input, Loader, useToast } from '../../components/ui'
import { VideoPlayer } from '../../components/streaming'
import { FlickSection } from '../../components/flick/FlickSection'
import { streamsApi, ingestApi, type IngestInfo, type StreamDetail } from '../../services/api/streams'
import { creatorsApi } from '../../services/api/creators'
import { categoriesApi, type Category } from '../../services/api/categories'
import { formatDate } from '../../utils/format'

/// Centre de contrôle du live (étape 16) : /creator/stream.
/// Programme (titre, catégorie, horaire), démarre, termine (VOD auto),
/// reprogramme. Statuts backend réels : SCHEDULED → LIVE → ENDED.
/// Clé de stream RÉELLE : émise par le serveur (`POST /streams/:id/stream-key`,
/// montrée une seule fois), jamais en dur.
export default function StreamManager() {
  const toast = useToast()
  const [channelId, setChannelId] = useState<number | null>(null)
  const [live, setLive] = useState<StreamDetail[]>([])
  const [scheduled, setScheduled] = useState<StreamDetail[]>([])
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [endingId, setEndingId] = useState<number | null>(null)
  const [startingId, setStartingId] = useState<number | null>(null)
  const [scheduling, setScheduling] = useState(false)
  const [editing, setEditing] = useState<StreamDetail | null>(null)
  const [title, setTitle] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [scheduledAt, setScheduledAt] = useState('')
  const [keyCopied, setKeyCopied] = useState(false)
  const [ingest, setIngest] = useState<IngestInfo | null>(null)
  const [ingestLoading, setIngestLoading] = useState(false)
  const [issuingKey, setIssuingKey] = useState(false)
  const [issuedKey, setIssuedKey] = useState<string | null>(null)
  const [quickTitle, setQuickTitle] = useState('')
  const [quickCategoryId, setQuickCategoryId] = useState('')
  const [goingLive, setGoingLive] = useState(false)

  function copyKey(text: string) {
    const done = () => {
      setKeyCopied(true)
      window.setTimeout(() => setKeyCopied(false), 1600)
    }
    if (navigator.clipboard) {
      void navigator.clipboard.writeText(text).then(done).catch(done)
    } else {
      done()
    }
  }

  const peakViewers = live.reduce((max, s) => Math.max(max, s.viewerCount), 0)
  const totalViewers = live.reduce((sum, s) => sum + s.viewerCount, 0)
  const ingestTarget = live[0] ?? scheduled[0] ?? null

  /**
   * PASSER EN DIRECT en un clic : programme → émet la clé → démarre.
   * La clé OBS s'affiche aussitôt dans le panneau ingest ci-dessous.
   */
  async function handleGoLiveNow() {
    if (channelId === null || goingLive) return
    if (live.length > 0) {
      toast.show('Un direct est déjà en cours — terminez-le avant en relancer un', 'info')
      return
    }
    setGoingLive(true)
    try {
      const now = new Date()
      const created = await streamsApi.schedule(channelId, {
        title: quickTitle.trim().length > 0
          ? quickTitle.trim()
          : `En direct ! (${now.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })})`,
        categoryId: quickCategoryId === '' ? null : Number(quickCategoryId),
        scheduledStartAt: now.toISOString(),
      })
      const key = await ingestApi.issueKey(created.id)
      setIssuedKey(key.obsStreamKey)
      await streamsApi.start(created.id)
      setQuickTitle('')
      await reload()
      toast.show('Tu es en direct ! Branche OBS avec la clé ci-dessous.', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Passage en direct impossible', 'error')
    } finally {
      setGoingLive(false)
    }
  }

  async function handleIssueKey(stream: StreamDetail) {
    setIssuingKey(true)
    try {
      const response = await ingestApi.issueKey(stream.id)
      setIssuedKey(response.obsStreamKey)
      setIngest((current) =>
        current !== null && current.streamId === stream.id
          ? { ...current, rtmpUrl: response.rtmpUrl, hasKey: true }
          : current,
      )
      toast.show('Clé émise — copiez-la maintenant, elle ne sera plus réaffichée', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Émission impossible', 'error')
    } finally {
      setIssuingKey(false)
    }
  }

  const reload = useCallback(async () => {
    const channel = await creatorsApi.getMyChannel()
    setChannelId(channel.id)
    const [livePage, scheduledPage] = await Promise.all([
      streamsApi.byCreator(channel.id, 'LIVE'),
      streamsApi.byCreator(channel.id, 'SCHEDULED'),
    ])
    setLive(livePage.content)
    setScheduled(scheduledPage.content)
    try {
      const cats = await categoriesApi.list(undefined, 0, 100)
      setCategories(cats.content)
    } catch {
      // Catégories optionnelles : la programmation reste possible sans.
    }
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

  // Infos d'ingest RÉELLES du direct ciblé (propriétaire, jamais la clé).
  useEffect(() => {
    if (ingestTarget === null) {
      setIngest(null)
      setIssuedKey(null)
      return
    }
    let cancelled = false
    setIngestLoading(true)
    ingestApi
      .info(ingestTarget.id)
      .then((info) => {
        if (!cancelled) setIngest(info)
      })
      .catch(() => {
        if (!cancelled) setIngest(null)
      })
      .finally(() => {
        if (!cancelled) setIngestLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [ingestTarget?.id])

  function resetForm() {
    setEditing(null)
    setTitle('')
    setCategoryId('')
    setScheduledAt('')
  }

  function startEdit(stream: StreamDetail) {
    setEditing(stream)
    setTitle(stream.title)
    setCategoryId(stream.categoryId !== null ? String(stream.categoryId) : '')
    setScheduledAt(stream.scheduledStartAt !== null ? stream.scheduledStartAt.slice(0, 16) : '')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  async function handleSchedule(event: FormEvent) {
    event.preventDefault()
    if (channelId === null || title.trim().length === 0) return
    setScheduling(true)
    try {
      const input = {
        title: title.trim(),
        categoryId: categoryId === '' ? null : Number(categoryId),
        scheduledStartAt: scheduledAt === '' ? null : new Date(scheduledAt).toISOString(),
      }
      if (editing !== null) {
        await streamsApi.update(editing.id, input)
        toast.show('Direct mis à jour', 'success')
      } else {
        await streamsApi.schedule(channelId, input)
        toast.show('Direct programmé', 'success')
      }
      resetForm()
      await reload()
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Programmation impossible', 'error')
    } finally {
      setScheduling(false)
    }
  }

  async function handleStart(stream: StreamDetail) {
    setStartingId(stream.id)
    try {
      await streamsApi.start(stream.id)
      await reload()
      toast.show('Direct en cours — bonne diffusion', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Démarrage impossible', 'error')
    } finally {
      setStartingId(null)
    }
  }

  async function handleEnd(stream: StreamDetail) {
    if (!window.confirm(`Terminer « ${stream.title} » ? Une VOD sera préparée automatiquement.`)) return
    setEndingId(stream.id)
    try {
      await streamsApi.end(stream.id)
      await reload()
      toast.show('Direct terminé — VOD en cours de préparation', 'success')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Fin du live impossible', 'error')
    } finally {
      setEndingId(null)
    }
  }

  if (loading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center" role="status" aria-label="Chargement des directs">
        <Loader size="lg" />
      </div>
    )
  }

  if (error !== null) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 p-6">
        <h1 className="font-display text-2xl font-bold text-text-primary">Gestion du live</h1>
        <Card>
          <p className="text-sm text-danger" role="alert">{error}</p>
          <p className="mt-1 text-xs text-text-muted">Astuce : ouvrez d'abord votre chaîne depuis le tableau de bord.</p>
        </Card>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-[1600px] space-y-6 p-4 lg:p-6">
      {/* Passer en direct en un clic */}
      <Card className={live.length > 0 ? 'border-live/50' : 'border-danger/40'}>
        <div className="flex flex-wrap items-center gap-3">
          <span aria-hidden="true" className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-lg ${live.length > 0 ? 'animate-pulse-live bg-live text-white' : 'bg-danger/15 text-danger'}`}>
            ●
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-display text-lg font-bold text-text-primary">
              {live.length > 0 ? 'Tu es en direct' : 'Passer en direct'}
            </p>
            <p className="text-sm text-text-secondary">
              {live.length > 0
                ? `« ${live[0].title} » — ta clé OBS est affichée ci-dessous, branche ton logiciel de diffusion.`
                : 'Un seul clic : crée le direct, génère ta clé OBS et démarre. Ensuite, lance le flux dans OBS.'}
            </p>
          </div>
          {live.length === 0 && (
            <button
              type="button"
              onClick={() => void handleGoLiveNow()}
              disabled={goingLive || channelId === null}
              className="flex h-12 shrink-0 items-center gap-2 rounded-xl bg-danger px-6 text-base font-black text-white shadow-lg transition-all hover:brightness-110 active:scale-95 disabled:cursor-wait disabled:opacity-60"
            >
              {goingLive ? (
                <>
                  <span aria-hidden="true" className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Démarrage…
                </>
              ) : (
                <>● Passer en direct</>
              )}
            </button>
          )}
        </div>
        {live.length === 0 && (
          <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
            <Input
              label="Titre du direct (optionnel)"
              name="quick-title"
              placeholder="Ex. Tournoi FIFA avec la commu"
              value={quickTitle}
              onChange={(e) => setQuickTitle(e.target.value)}
              maxLength={120}
            />
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-text-secondary">Catégorie (optionnel)</span>
              <select
                value={quickCategoryId}
                onChange={(e) => setQuickCategoryId(e.target.value)}
                className="w-full rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
              >
                <option value="">Sans catégorie</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </label>
          </div>
        )}
      </Card>

      <FlickSection title="Studio Créateur" subtitle="Prévisualisation du flux, clé de stream et analytics" />

      {/* Studio : contrôle réel + ingest réel */}
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0">
          <VideoPlayer hlsUrl={live[0]?.playbackUrl ?? null} />
          <p className="mt-2 text-xs text-text-muted">
            {live.length > 0
              ? 'Contrôle de votre flux en direct (même flux que vos spectateurs).'
              : 'Branchez OBS sur le serveur RTMP ci-contre, puis démarrez un direct programmé ci-dessous.'}
          </p>
        </div>
        <div className="space-y-3">
          <Card>
            <p className="flex items-center gap-1.5 text-sm font-bold text-text-primary">
              <Key size={16} className="text-primary-400" />
              Clé de stream & RTMP
            </p>
            {ingestTarget === null ? (
              <p className="mt-3 text-sm text-text-secondary">
                Programmez un direct ci-dessous pour obtenir vos paramètres OBS.
              </p>
            ) : ingestLoading ? (
              <p className="mt-3 text-sm text-text-muted" role="status">Chargement des paramètres…</p>
            ) : ingest === null ? (
              <p className="mt-3 text-sm text-danger" role="alert">Paramètres indisponibles pour le moment.</p>
            ) : (
            <div className="mt-3 space-y-2.5 text-sm">
              <p className="text-xs text-text-muted">
                Direct : <span className="font-semibold text-text-primary">{ingestTarget.title}</span>
              </p>
              <div>
                <p className="text-xs text-text-muted">URL du serveur (champ « serveur » d'OBS)</p>
                <div className="mt-1 flex items-center gap-2">
                  <code className="min-w-0 flex-1 truncate rounded-lg bg-bg-elevated px-2.5 py-1.5 font-mono text-xs text-text-primary">{ingest.rtmpUrl}</code>
                  <button type="button" onClick={() => copyKey(ingest.rtmpUrl)} aria-label="Copier l'URL RTMP" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border-default text-text-secondary hover:text-text-primary">
                    {keyCopied ? <Check size={15} className="text-success" /> : <Copy size={15} />}
                  </button>
                </div>
              </div>
              <div>
                <p className="text-xs text-text-muted">Clé de stream (champ « clé » d'OBS : ne jamais partager)</p>
                {issuedKey !== null ? (
                  <div className="mt-1 space-y-1.5">
                    <div className="flex items-center gap-2">
                      <code className="min-w-0 flex-1 truncate rounded-lg border border-warning/40 bg-warning/10 px-2.5 py-1.5 font-mono text-xs text-text-primary">{issuedKey}</code>
                      <button type="button" onClick={() => copyKey(issuedKey)} aria-label="Copier la clé" className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-border-default text-text-secondary hover:text-text-primary">
                        {keyCopied ? <Check size={15} className="text-success" /> : <Copy size={15} />}
                      </button>
                    </div>
                    <p className="text-xs text-warning" role="alert">
                      Copiez-la maintenant : elle ne sera plus jamais réaffichée.
                    </p>
                    <button type="button" onClick={() => setIssuedKey(null)} className="text-xs font-semibold text-text-muted hover:text-text-primary">
                      J'ai copié ma clé
                    </button>
                  </div>
                ) : (
                  <div className="mt-1 flex items-center gap-2">
                    <code className="min-w-0 flex-1 truncate rounded-lg bg-bg-elevated px-2.5 py-1.5 font-mono text-xs text-text-primary">
                      {ingest.hasKey ? '•••••••• (clé active)' : 'Aucune clé émise'}
                    </code>
                    <button
                      type="button"
                      onClick={() => void handleIssueKey(ingestTarget)}
                      disabled={issuingKey}
                      className="h-8 shrink-0 rounded-lg bg-primary-500 px-2.5 text-xs font-bold text-text-inverse hover:bg-primary-400 disabled:opacity-60"
                    >
                      {issuingKey ? '…' : ingest.hasKey ? 'Régénérer' : 'Générer'}
                    </button>
                  </div>
                )}
                {ingest.playbackUrl && (
                  <p className="mt-1 truncate font-mono text-[11px] text-text-muted" title={ingest.playbackUrl}>
                    HLS : {ingest.playbackUrl}
                  </p>
                )}
              </div>
            </div>
            )}
          </Card>
          <div className="grid grid-cols-3 gap-2">
            {[
              { label: 'En direct', value: String(live.length), Icon: Eye },
              { label: 'Viewers', value: String(totalViewers), Icon: Users },
              { label: 'Pic', value: String(peakViewers), Icon: TrendUp },
            ].map(({ label, value, Icon }) => (
              <Card key={label} className="p-3 text-center">
                <Icon size={17} className="mx-auto text-primary-400" />
                <p className="mt-1 text-lg font-black text-text-primary">{value}</p>
                <p className="text-[11px] text-text-muted">{label}</p>
              </Card>
            ))}
          </div>
        </div>
      </div>

      <h1 className="font-display text-2xl font-bold text-text-primary">Gestion du live</h1>

      <Card>
        <p className="text-sm font-semibold text-text-primary">
          {editing !== null ? `Modifier « ${editing.title} »` : 'Programmer un direct'}
        </p>
        <form onSubmit={handleSchedule} className="mt-3 space-y-3" noValidate>
          <Input
            label="Titre du direct"
            name="stream-title"
            placeholder="Tournoi FIFA avec la commu"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={120}
          />
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-text-secondary">Catégorie</span>
              <select
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                className="w-full rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
              >
                <option value="">Sans catégorie</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </label>
            <Input
              label="Début programmé (optionnel)"
              name="stream-scheduled-at"
              type="datetime-local"
              value={scheduledAt}
              onChange={(e) => setScheduledAt(e.target.value)}
            />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" variant="primary" size="sm" loading={scheduling} disabled={scheduling || title.trim().length === 0}>
              {editing !== null ? 'Enregistrer' : 'Programmer'}
            </Button>
            {editing !== null && (
              <Button type="button" variant="ghost" size="sm" onClick={resetForm}>
                Annuler
              </Button>
            )}
          </div>
        </form>
      </Card>

      <section aria-label="Directs en cours">
        <h2 className="mb-2 font-display text-lg font-bold text-text-primary">En cours</h2>
        {live.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">
              Aucun direct en cours — lancez votre logiciel de diffusion (OBS), puis démarrez
              un direct programmé ci-dessous.
            </p>
          </Card>
        ) : (
          <div className="space-y-3">
            {live.map((stream) => (
              <Card key={stream.id}>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="flex flex-wrap items-center gap-2 font-semibold text-text-primary">
                      <Badge variant="live" dot>LIVE</Badge>
                      <span className="truncate">{stream.title}</span>
                    </p>
                    <p className="mt-1 text-xs text-text-muted">
                      {stream.viewerCount} spectateurs · pic {stream.peakViewers}
                    </p>
                  </div>
                  <Button
                    variant="danger"
                    size="sm"
                    loading={endingId === stream.id}
                    onClick={() => handleEnd(stream)}
                  >
                    Terminer le live
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section aria-label="Directs programmés">
        <h2 className="mb-2 font-display text-lg font-bold text-text-primary">Programmés</h2>
        {scheduled.length === 0 ? (
          <Card>
            <p className="text-sm text-text-secondary">Aucun direct programmé.</p>
          </Card>
        ) : (
          <div className="space-y-3">
            {scheduled.map((stream) => (
              <Card key={stream.id}>
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold text-text-primary">{stream.title}</p>
                    <p className="mt-1 text-xs text-text-muted">
                      {stream.scheduledStartAt !== null ? formatDate(stream.scheduledStartAt) : 'Sans horaire'}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    <Button
                      variant="primary"
                      size="sm"
                      loading={startingId === stream.id}
                      onClick={() => handleStart(stream)}
                    >
                      Démarrer
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => startEdit(stream)}>
                      Modifier
                    </Button>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
