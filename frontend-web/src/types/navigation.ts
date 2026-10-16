import type { IconName } from '../components/ui/icons'

/** Élément de navigation utilisé par les Sidebars. */
export interface NavItem {
  /** Chemin de la route (ex: '/explorer') */
  to: string
  /** Libellé affiché (repli si `labelKey` intraduisible) */
  label: string
  /** Icône du design system */
  icon: IconName
  /** Clé i18n du libellé (étape 19 : `nav.*`, repli = `label`) */
  labelKey?: string
}