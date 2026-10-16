import { useEffect, useState, type FormEvent } from 'react'
import { Button, Card, Input, Loader, useToast } from '../../components/ui'
import { ConfirmModal } from '../../components/admin/ConfirmModal'
import { categoriesApi, type Category } from '../../services/api/categories'
import { usePageMeta } from '../../hooks/usePageMeta'

/// Gestion des catégories (étape 17) : /admin/categories.
/// CRUD sur le catalogue existant (ADMIN) — jamais de doublons (conflit
/// backend si nom/slug déjà pris).
export default function AdminCategories() {
  usePageMeta('Catégories', 'Catalogue des catégories de la plateforme')
  const toast = useToast()
  const [categories, setCategories] = useState<Category[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [editing, setEditing] = useState<Category | null>(null)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<Category | null>(null)
  const [acting, setActing] = useState(false)

  async function reload() {
    const result = await categoriesApi.list(undefined, 0, 100)
    setCategories(result.content)
  }

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
  }, [])

  function startEdit(category: Category) {
    setEditing(category)
    setName(category.name)
    setDescription(category.description ?? '')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function resetForm() {
    setEditing(null)
    setName('')
    setDescription('')
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault()
    if (name.trim().length === 0) return
    setSaving(true)
    try {
      const input = { name: name.trim(), description: description.trim() === '' ? null : description.trim() }
      if (editing !== null) {
        const updated = await categoriesApi.update(editing.id, input)
        setCategories((current) => current.map((c) => (c.id === updated.id ? updated : c)))
        toast.show('Catégorie mise à jour', 'success')
      } else {
        const created = await categoriesApi.create(input)
        setCategories((current) => [...current, created])
        toast.show('Catégorie créée', 'success')
      }
      resetForm()
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Enregistrement impossible', 'error')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (deleting === null) return
    setActing(true)
    try {
      await categoriesApi.remove(deleting.id)
      setCategories((current) => current.filter((c) => c.id !== deleting.id))
      setDeleting(null)
      toast.show('Catégorie supprimée', 'info')
    } catch (err) {
      toast.show(err instanceof Error ? err.message : 'Suppression impossible', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-4 p-6">
      <h1 className="font-display text-2xl font-bold text-text-primary">Catégories</h1>

      <Card>
        <p className="text-sm font-semibold text-text-primary">
          {editing !== null ? `Modifier « ${editing.name} »` : 'Nouvelle catégorie'}
        </p>
        <form onSubmit={handleSave} className="mt-3 space-y-3" noValidate>
          <Input
            label="Nom"
            name="category-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={100}
            placeholder="Gaming"
          />
          <Input
            label="Description (optionnelle)"
            name="category-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
          />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" variant="primary" size="sm" loading={saving} disabled={saving || name.trim() === ''}>
              {editing !== null ? 'Enregistrer' : 'Créer'}
            </Button>
            {editing !== null && (
              <Button type="button" variant="ghost" size="sm" onClick={resetForm}>Annuler</Button>
            )}
          </div>
        </form>
      </Card>

      {loading ? (
        <div className="flex min-h-[20vh] items-center justify-center" role="status" aria-label="Chargement">
          <Loader size="lg" />
        </div>
      ) : error !== null ? (
        <Card><p className="text-sm text-danger" role="alert">{error}</p></Card>
      ) : categories.length === 0 ? (
        <Card><p className="text-sm text-text-secondary">Aucune catégorie.</p></Card>
      ) : (
        <div className="space-y-2">
          {categories.map((category) => (
            <Card key={category.id}>
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="min-w-0">
                  <p className="font-semibold text-text-primary">
                    {category.name} <span className="font-mono text-xs text-text-muted">/{category.slug}</span>
                  </p>
                  {category.description !== null && (
                    <p className="mt-0.5 line-clamp-1 text-xs text-text-secondary">{category.description}</p>
                  )}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Button variant="ghost" size="sm" onClick={() => startEdit(category)}>Modifier</Button>
                  <Button variant="ghost" size="sm" onClick={() => setDeleting(category)}>Supprimer</Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      <ConfirmModal
        open={deleting !== null}
        title={`Supprimer « ${deleting?.name ?? ''} » ?`}
        message="Les contenus déjà rattachés gardent leur référence : vérifiez qu’aucune chaîne active n’en dépend comme catégorie principale."
        confirmLabel="Supprimer"
        danger
        loading={acting}
        onClose={() => setDeleting(null)}
        onConfirm={() => handleDelete()}
      />
    </div>
  )
}
