import { useState } from 'react'
import { Button, Card, Input, Loader, useToast } from '../../components/ui'
import { aiApi } from '../../services/api/ai'
import { ApiError } from '../../services/http'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Assistant Flick (étape 18) : /assistant.
/// Répond depuis l'aide officielle (suivi, créateur, coins, VOD,
/// modération…). Exemples : « Comment suivre une chaîne ? »,
/// « Comment acheter des coins ? », « Comment lancer un live ? ».
interface Exchange {
  question: string
  answer: string
  sources: string[]
}

const EXAMPLES = [
  'Comment suivre une chaîne ?',
  'Comment devenir créateur ?',
  'Comment acheter des coins ?',
  'Comment lancer un live ?',
]

export default function Assistant() {
  usePageMeta('Assistant Flick', 'Aide officielle et découverte')
  const toast = useToast()
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [history, setHistory] = useState<Exchange[]>([])

  async function ask(text: string) {
    const clean = text.trim()
    if (!clean || loading) return
    setLoading(true)
    try {
      const result = await aiApi.assistant(clean)
      setHistory((current) => [
        { question: clean, answer: result.answer, sources: result.sources },
        ...current,
      ])
      setQuestion('')
    } catch (err) {
      toast.show(
        err instanceof ApiError ? err.message : 'Assistant indisponible',
        'error',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4 p-6">
      <div>
        <h1 className="font-display text-2xl font-bold text-text-primary">Assistant Flick</h1>
        <p className="mt-1 text-sm text-text-secondary">
          Que puis-je faire pour vous ? Recherche, découverte, fonctionnement de Flick.
        </p>
      </div>

      <Card>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            void ask(question)
          }}
        >
          <Input
            label="Votre question"
            name="assistant-question"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="Écrivez votre demande…"
            maxLength={1000}
          />
          <div className="flex items-end">
            <Button type="submit" disabled={loading || !question.trim()} aria-label="Envoyer">
              {loading ? '…' : '➤'}
            </Button>
          </div>
        </form>
        <div className="mt-3 flex flex-wrap gap-2">
          {EXAMPLES.map((example) => (
            <Button key={example} variant="secondary" size="sm" onClick={() => void ask(example)}>
              {example}
            </Button>
          ))}
        </div>
      </Card>

      {loading && <Loader />}

      <div className="space-y-3" aria-live="polite">
        {history.map((exchange, index) => (
          <Card key={`${exchange.question}-${index}`}>
            <p className="text-sm font-semibold text-text-primary">{exchange.question}</p>
            <p className="mt-2 text-sm text-text-secondary">{exchange.answer}</p>
            {exchange.sources.length > 0 && (
              <p className="mt-2 text-xs text-text-secondary">
                Sources officielles : {exchange.sources.join(', ')}
              </p>
            )}
          </Card>
        ))}
      </div>
    </div>
  )
}
