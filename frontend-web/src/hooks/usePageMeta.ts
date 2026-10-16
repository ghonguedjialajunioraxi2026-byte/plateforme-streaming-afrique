import { useEffect } from 'react'
import { APP_NAME, SITE_URL } from '../constants/app'

/// Métadonnées de partage (étape 15, §62 ; canonical/OG étape 25) — SPA
/// sans SSR : titre d'onglet + meta description + canonical + Open Graph
/// à jour (aperçus de partage, historique, accessibilité). Restaurées au
/// démontage. Limite connue : les crawlers sans JS voient le shell
/// statique (pré-rendu SSR/SSG = chantier futur).
export function usePageMeta(
  title: string | null,
  description: string | null,
  image?: string,
): void {
  useEffect(() => {
    const previousTitle = document.title
    const selector = 'meta[name="description"]'
    let tag = document.head.querySelector<HTMLMetaElement>(selector)
    const previousDescription = tag?.getAttribute('content') ?? null
    let created = false
    if (title !== null) {
      document.title = `${title} — ${APP_NAME}`
    }
    if (description !== null) {
      if (tag === null) {
        tag = document.createElement('meta')
        tag.setAttribute('name', 'description')
        document.head.appendChild(tag)
        created = true
      }
      tag.setAttribute('content', description.slice(0, 160))
    }
    // Canonical + Open Graph absolus (domaine = SITE_URL configurable).
    const path = window.location.pathname
    upsertLink('canonical', `${SITE_URL}${path}`)
    upsertMeta('og:title', document.title)
    if (description !== null) upsertMeta('og:description', description.slice(0, 160))
    upsertMeta('og:url', `${SITE_URL}${path}`)
    if (image !== undefined) upsertMeta('og:image', image.startsWith('http') ? image : `${SITE_URL}${image}`)
    return () => {
      document.title = previousTitle
      if (created) {
        tag?.remove()
      } else if (previousDescription !== null) {
        tag?.setAttribute('content', previousDescription)
      }
    }
  }, [title, description, image])
}

function upsertLink(rel: string, href: string): void {
  let link = document.head.querySelector<HTMLLinkElement>(`link[rel="${rel}"]`)
  if (link === null) {
    link = document.createElement('link')
    link.setAttribute('rel', rel)
    document.head.appendChild(link)
  }
  link.setAttribute('href', href)
}

function upsertMeta(property: string, content: string): void {
  let tag = document.head.querySelector<HTMLMetaElement>(`meta[property="${property}"]`)
  if (tag === null) {
    tag = document.createElement('meta')
    tag.setAttribute('property', property)
    document.head.appendChild(tag)
  }
  tag.setAttribute('content', content)
}
