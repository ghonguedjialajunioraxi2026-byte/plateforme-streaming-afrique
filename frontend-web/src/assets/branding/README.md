# FLICK — Logo officiel (SOURCE UNIQUE)

**Un seul fichier : `FLICK.png`.** C'est le logo officiel de l'application,
affiché partout via le composant central `src/components/branding/FlickLogo.tsx`.
Aucun autre logo n'existe dans le projet (anciens SVG, favicon SVG et logos
de templates ont été supprimés).

## Remplacer le logo

1. Déposez le nouveau logo en gardant **exactement le même nom** : `FLICK.png`
   (dans ce dossier).
2. Copiez-le aussi vers `frontend-web/public/FLICK.png` (favicon servi,
   référencé dans `index.html`).
3. Recompilez (`npm run build` / le dev server recharge à chaud).
4. C'est tout : `FlickLogo` est le seul consommateur, aucun composant
   (`Navbar`, `Sidebar`, `Login`, …) n'a besoin d'être modifié.

Contraintes : gardez un PNG à fond transparent et des proportions carrées
(le composant impose une hauteur + `w-auto` : le logo n'est jamais déformé).

## Règle

Aucun composant ne doit contenir de SVG inline du logo, de texte imitant
le logo, ni d'import d'image de logo hors de ce dossier. Tout passe par
`src/constants/branding.ts` + `FlickLogo`.
