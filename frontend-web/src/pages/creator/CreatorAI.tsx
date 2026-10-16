import { useState } from 'react'
import { Badge, Button, Card, Input, Loader, Tabs, useToast } from '../../components/ui'
import { aiApi } from '../../services/api/ai'
import { ApiError } from '../../services/http'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Assistant IA du studio (étape 18) : /creator/ai.
/// Suggestions = PROPOSITIONS (copier / utiliser / refuser — jamais
/// d'écriture automatique). Quotas et erreurs backend affichés tels quels.
type TabKey = 'titles' | 'description' | 'tags' | 'vod' | 'analytics'

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message
  return err instanceof Error ? err.message : 'Échec de la génération'
}

function useCopy(toast: ReturnType<typeof useToast>) {
  return async (text: string) => {
    try {
      await navigator.clipboard.writeText(text)
      toast.show('Copié dans le presse-papiers', 'success')
    } catch {
      toast.show('Copie impossible — sélectionne le texte', 'error')
    }
  }
}

function TitlesPanel() {
  const toast = useToast()
  const copy = useCopy(toast)
  const [topic, setTopic] = useState('')
  const [category, setCategory] = useState('')
  const [loading, setLoading] = useState(false)
  const [suggestions, setSuggestions] = useState<string[]>([])

  async function generate() {
    if (!topic.trim()) return
    setLoading(true)
    try {
      const result = await aiApi.titles({ topic: topic.trim(), category: category.trim() || undefined })
      setSuggestions(result.suggestions)
    } catch (err) {
      toast.show(errorMessage(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-3">
      <Input label="Sujet du live" name="topic" value={topic} onChange={(e) => setTopic(e.target.value)} placeholder="Ex. match de foot ce soir" maxLength={500} />
      <Input label="Catégorie (optionnel)" name="category" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Ex. Sport" maxLength={100} />
      <Button onClick={generate} disabled={loading || !topic.trim()}>
        {loading ? 'Génération…' : 'Suggérer 3 titres'}
      </Button>
      {loading && <Loader />}
      <ul className="space-y-2">
        {suggestions.map((suggestion) => (
          <li key={suggestion}>
            <Card>
              <p className="text-sm font-semibold text-text-primary">{suggestion}</p>
              <div className="mt-2 flex gap-2">
                <Button variant="secondary" size="sm" onClick={() => void copy(suggestion)}>Copier</Button>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </div>
  )
}

function DescriptionPanel() {
  const toast = useToast()
  const copy = useCopy(toast)
  const [text, setText] = useState('')
  const [mode, setMode] = useState<'create' | 'improve'>('create')
  const [loading, setLoading] = useState(false)
  const [proposal, setProposal] = useState<string | null>(null)

  async function generate() {
    if (!text.trim()) return
    setLoading(true)
    try {
      const result = await aiApi.description({ text: text.trim(), mode })
      setProposal(result.proposal)
    } catch (err) {
      toast.show(errorMessage(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex gap-2" role="group" aria-label="Mode">
        <Button variant={mode === 'create' ? 'primary' : 'secondary'} size="sm" onClick={() => setMode('create')}>Créer</Button>
        <Button variant={mode === 'improve' ? 'primary' : 'secondary'} size="sm" onClick={() => setMode('improve')}>Améliorer</Button>
      </div>
      <label className="block text-sm">
        <span className="mb-1 block font-medium text-text-primary">Texte de départ</span>
        <textarea
          className="w-full rounded-lg border border-border bg-surface p-3 text-sm text-text-primary"
          rows={4}
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={2000}
          placeholder="Décris ton live en quelques mots…"
        />
      </label>
      <Button onClick={generate} disabled={loading || !text.trim()}>
        {loading ? 'Génération…' : mode === 'create' ? 'Rédiger' : 'Améliorer'}
      </Button>
      {loading && <Loader />}
      {proposal && (
        <Card>
          <p className="text-sm text-text-primary">{proposal}</p>
          <div className="mt-2 flex gap-2">
            <Button variant="secondary" size="sm" onClick={() => void copy(proposal)}>Copier</Button>
            <Button variant="secondary" size="sm" onClick={() => setProposal(null)}>Refuser</Button>
          </div>
        </Card>
      )}
    </div>
  )
}

function TagsPanel() {
  const toast = useToast()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(false)
  const [tags, setTags] = useState<string[]>([])

  async function generate() {
    if (!title.trim()) return
    setLoading(true)
    try {
      const result = await aiApi.tags({ title: title.trim(), description: description.trim() || undefined })
      setTags(result.tags)
    } catch (err) {
      toast.show(errorMessage(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-3">
      <Input label="Titre" name="tags-title" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={300} />
      <Input label="Description (optionnel)" name="tags-desc" value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} />
      <Button onClick={generate} disabled={loading || !title.trim()}>
        {loading ? 'Génération…' : 'Proposer des tags'}
      </Button>
      {loading && <Loader />}
      {tags.length > 0 && (
        <p className="flex flex-wrap gap-2">
          {tags.map((tag) => (
            <Badge key={tag} variant="neutral">#{tag}</Badge>
          ))}
        </p>
      )}
    </div>
  )
}

function VodPanel() {
  const toast = useToast()
  const [videoId, setVideoId] = useState('')
  const [loading, setLoading] = useState(false)
  const [summary, setSummary] = useState<string | null>(null)
  const [points, setPoints] = useState<string[]>([])
  const [clips, setClips] = useState<string[]>([])

  async function load() {
    const id = Number(videoId)
    if (!Number.isFinite(id) || id <= 0) {
      toast.show('Identifiant de VOD invalide', 'error')
      return
    }
    setLoading(true)
    try {
      const [resume, tranches] = await Promise.all([
        aiApi.vodSummary(id),
        aiApi.clipSuggestions(id),
      ])
      setSummary(resume.summary)
      setPoints(resume.keyPoints)
      setClips(tranches.keyPoints)
    } catch (err) {
      toast.show(errorMessage(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-text-secondary">
        Résumé depuis les métadonnées uniquement — aucune analyse audio/vidéo prétendue.
        Tranches de clips indicatives, à vérifier avant publication.
      </p>
      <Input label="Identifiant de ta VOD" name="vod-id" value={videoId} onChange={(e) => setVideoId(e.target.value)} inputMode="numeric" placeholder="Ex. 12" />
      <Button onClick={load} disabled={loading || !videoId.trim()}>
        {loading ? 'Analyse…' : 'Résumer ma VOD'}
      </Button>
      {loading && <Loader />}
      {summary && (
        <Card>
          <p className="text-sm text-text-primary">{summary}</p>
          {points.length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-text-secondary">
              {points.map((point) => <li key={point}>{point}</li>)}
            </ul>
          )}
        </Card>
      )}
      {clips.length > 0 && (
        <Card>
          <p className="text-sm font-semibold text-text-primary">Tranches suggérées <Badge variant="neutral">à vérifier</Badge></p>
          <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-text-secondary">
            {clips.map((clip) => <li key={clip}>{clip}</li>)}
          </ul>
        </Card>
      )}
    </div>
  )
}

function AnalyticsPanel() {
  const toast = useToast()
  const [question, setQuestion] = useState('Pourquoi mes viewers diminuent ?')
  const [loading, setLoading] = useState(false)
  const [analysis, setAnalysis] = useState<string | null>(null)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const [sufficient, setSufficient] = useState(true)

  async function ask() {
    if (!question.trim()) return
    setLoading(true)
    try {
      const result = await aiApi.analytics(question.trim())
      setAnalysis(result.analysis)
      setSuggestions(result.suggestions)
      setSufficient(result.dataSufficient)
    } catch (err) {
      toast.show(errorMessage(err), 'error')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-xs text-text-secondary">
        Analyse tes chiffres réels — l'assistant signale les données insuffisantes au lieu d'inventer.
      </p>
      <Input label="Ta question" name="analytics-q" value={question} onChange={(e) => setQuestion(e.target.value)} maxLength={1000} />
      <Button onClick={ask} disabled={loading || !question.trim()}>
        {loading ? 'Analyse…' : 'Analyser'}
      </Button>
      {loading && <Loader />}
      {analysis && (
        <Card>
          {!sufficient && <p><Badge variant="neutral">Données insuffisantes</Badge></p>}
          <p className="mt-1 text-sm text-text-primary">{analysis}</p>
          {suggestions.length > 0 && (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-text-secondary">
              {suggestions.map((suggestion) => <li key={suggestion}>{suggestion}</li>)}
            </ul>
          )}
        </Card>
      )}
    </div>
  )
}

export default function CreatorAI() {
  usePageMeta('Assistant IA', 'Titres, descriptions, tags, VOD et analytics du studio')
  const [tab, setTab] = useState<TabKey>('titles')

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-text-primary">Assistant IA</h1>
        <p className="mt-1 text-sm text-text-secondary">
          Propositions à valider — rien n'est appliqué sans ton accord.
        </p>
      </div>
      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as TabKey)}
        items={[
          { key: 'titles', label: 'Titres' },
          { key: 'description', label: 'Description' },
          { key: 'tags', label: 'Tags' },
          { key: 'vod', label: 'VOD & Clips' },
          { key: 'analytics', label: 'Analytics' },
        ]}
      />
      <Card>
        {tab === 'titles' && <TitlesPanel />}
        {tab === 'description' && <DescriptionPanel />}
        {tab === 'tags' && <TagsPanel />}
        {tab === 'vod' && <VodPanel />}
        {tab === 'analytics' && <AnalyticsPanel />}
      </Card>
    </div>
  )
}
