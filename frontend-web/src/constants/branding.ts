/// Branding FLICK — SOURCE UNIQUE du logo officiel : `FLICK.png`.
///
/// Toute l'application affiche le logo via le composant central
/// `src/components/branding/FlickLogo.tsx`, qui importe ce seul fichier.
/// Pour changer de logo : remplacer `src/assets/branding/FLICK.png`
/// en gardant le même nom — aucun composant n'a besoin d'être modifié.
///
/// Le favicon servi est `public/FLICK.png` (copie déployée du même
/// fichier, référencée dans `index.html`).
import flickPng from '../assets/branding/FLICK.png'

/// Nom officiel de la marque (texte, métadonnées, accessibilité).
export const BRAND_NAME = 'FLICK'

/// Texte alternatif du logo — toujours "FLICK", jamais "logo".
export const BRAND_ALT = 'FLICK'

/// Destination du logo cliquable (accueil).
export const BRAND_HOME_PATH = '/'

/// Logo officiel FLICK — utilisé partout (variantes `full` et `mark`).
export const BRAND_LOGO_SRC: string = flickPng

/// Alias du logo officiel (variante compacte : même fichier).
export const BRAND_MARK_SRC: string = flickPng

/// Alias du logo officiel (favicon : même fichier, voir `public/FLICK.png`).
export const BRAND_FAVICON_SRC: string = flickPng
