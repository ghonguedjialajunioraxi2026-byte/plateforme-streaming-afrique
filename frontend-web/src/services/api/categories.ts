/// Service catégories (étape 09) — miroir RÉEL de `CategoryController`.
/// Lecture publique : liste paginée (recherche backend), fiche par id.
import { http } from '../http'

export interface Category {
  id: number
  name: string
  slug: string
  description: string | null
}

export interface CategoryPage {
  content: Category[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export const categoriesApi = {
  list: (search?: string, page = 0, size = 50) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (search !== undefined && search.trim().length > 0) {
      params.set('search', search.trim())
    }
    return http.get<CategoryPage>(`/categories?${params.toString()}`)
  },

  getById: (id: number) => http.get<Category>(`/categories/${id}`),

  /** Créer une catégorie (ADMIN). */
  create: (input: { name: string; description?: string | null }) =>
    http.post<Category>('/categories', input),

  /** Modifier une catégorie (ADMIN). */
  update: (id: number, input: { name: string; description?: string | null }) =>
    http.put<Category>(`/categories/${id}`, input),

  /** Supprimer une catégorie (ADMIN). */
  remove: (id: number) => http.delete<null>(`/categories/${id}`),
}
