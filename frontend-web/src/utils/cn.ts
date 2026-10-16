/**
 * Fusionne plusieurs classes CSS en ignorant les valeurs falsy.
 *
 * Exemple :
 * cn('px-4', isActive && 'text-primary', null) // => 'px-4 text-primary'
 */
export function cn(
  ...classes: Array<string | false | null | undefined>
): string {
  return classes.filter(Boolean).join(' ')
}