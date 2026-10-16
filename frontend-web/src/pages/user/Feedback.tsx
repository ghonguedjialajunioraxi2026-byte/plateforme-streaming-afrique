import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useLocation } from 'react-router'
import { Button, Card, EmptyState, Input, Loader, useToast } from '../../components/ui'
import { usePageMeta } from '../../hooks/usePageMeta'
import {
  BETA_APP_VERSION,
  betaApi,
  type BetaFeedback,
  type BetaFeedbackCategory,
} from '../../services/api/beta'

const CATEGORIES: { key: BetaFeedbackCategory; label: string }[] = [
  { key: 'BUG', label: 'Bug' },
  { key: 'UX', label: 'Problème d\u2019ergonomie' },
  { key: 'SUGGESTION', label: 'Suggestion' },
  { key: 'PERFORMANCE', label: 'Performance' },
  { key: 'STREAMING', label: 'Streaming' },
  { key: 'CHAT', label: 'Chat' },
  { key: 'PAYMENT', label: 'Paiement' },
  { key: 'OTHER', label: 'Autre' },
]

/// Retours bêta (étape 24) : /feedback.
/// Formulaire (catégorie + titre + description, contexte non sensible
/// pré-rempli : route courante + version) + mes signalements.
/// Consentement : seul le contexte affiché est envoyé, jamais de
/// mot de passe/token/contenu privé.
export default function Feedback() {
  usePageMeta('Retour bêta', 'Signale un bug ou propose une amélioration')
  const toast = useToast()
  const location = useLocation()
  const [category, setCategory] = useState<BetaFeedbackCategory>('BUG')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [items, setItems] = useState<BetaFeedback[]>([])
  const [loading, setLoading] = useState(true)

  const reload = useCallback(async () => {
    const page = await betaApi.myFeedback(0, 20)
    setItems(page.content)
  }, [])

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      try {
        await reload()
      } catch {
        if (!cancelled) setItems([])
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [reload])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (title.trim() === '' || description.trim() === '') {
      toast.show('Titre et description sont requis', 'error')
      return
    }
    setSubmitting(true)
    try {
      await betaApi.submitFeedback({
        category,
        title: title.trim(),
        description: description.trim(),
        route: location.pathname,
        appVersion: BETA_APP_VERSION,
        device: navigator.userAgent.slice(0, 120),
      })
      setTitle('')
      setDescription('')
      toast.show('Retour enregistré, merci !', 'success')
      await reload()
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Envoi impossible', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Retour bêta</h1>
      <p className="text-sm text-text-secondary">
        Un bug, une friction, une idée ? L&apos;équipe lit chaque retour.
        Contexte envoyé : page ({location.pathname}), version ({BETA_APP_VERSION}),
        appareil — jamais de mot de passe ni de contenu privé.
      </p>

      <Card>
        <form onSubmit={handleSubmit} className="space-y-3" noValidate>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Catégorie</span>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as BetaFeedbackCategory)}
              className="w-full rounded-lg border border-border bg-bg-elevated px-3 py-2 text-sm text-text-primary"
            >
              {CATEGORIES.map((c) => <option key={c.key} value={c.key}>{c.label}</option>)}
            </select>
          </label>
          <Input
            label="Titre"
            name="beta-feedback-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Le lecteur se fige après 10 s"
          />
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-text-secondary">Description</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Étapes pour reproduire, résultat attendu…"
              rows={5}
              maxLength={4000}
              className="w-full rounded-lg border border-border-default bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted"
            />
          </label>
          <Button type="submit" loading={submitting}>Envoyer le retour</Button>
        </form>
      </Card>

      <h2 className="text-lg font-bold text-text-primary">Mes signalements</h2>
      {loading ? (
        <Loader />
      ) : items.length === 0 ? (
        <EmptyState title="Aucun retour" description="Tes signalements apparaîtront ici avec leur statut." />
      ) : (
        <div className="space-y-2">
          {items.map((item) => (
            <Card key={item.id}>
              <div className="font-medium">[{item.category}] {item.title}</div>
              <div className="mt-1 text-xs text-text-muted">
                #{item.id} · {item.status}{item.priority !== null && ` · ${item.priority}`} ·{' '}
                {new Date(item.createdAt).toLocaleDateString()}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
