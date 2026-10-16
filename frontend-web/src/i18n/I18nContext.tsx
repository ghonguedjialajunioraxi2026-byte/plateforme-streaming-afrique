import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { DEFAULT_LANGUAGE, dictionaries, SUPPORTED_LANGUAGES, type I18nKey, type SupportedLanguage } from './dictionaries'

/// Contexte i18n (étape 19) — sans dépendance externe.
/// Résolution : préférence profil > localStorage > navigateur > `fr`.
/// La préférence utilisateur a TOUJOURS priorité.

const STORAGE_KEY = 'flick-language'

function isSupported(value: string | null | undefined): value is SupportedLanguage {
  return value === 'fr' || value === 'en'
}

function browserLanguage(): SupportedLanguage {
  if (typeof navigator === 'undefined') return DEFAULT_LANGUAGE
  const code = navigator.language.slice(0, 2).toLowerCase()
  return isSupported(code) ? code : DEFAULT_LANGUAGE
}

function storedLanguage(): SupportedLanguage | null {
  try {
    return isSupported(localStorage.getItem(STORAGE_KEY))
      ? (localStorage.getItem(STORAGE_KEY) as SupportedLanguage)
      : null
  } catch {
    return null
  }
}

interface I18nContextValue {
  lang: SupportedLanguage
  setLang: (lang: SupportedLanguage) => void
  t: (key: I18nKey) => string
}

const I18nContext = createContext<I18nContextValue>({
  lang: DEFAULT_LANGUAGE,
  setLang: () => undefined,
  t: (key) => dictionaries[DEFAULT_LANGUAGE][key],
})

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<SupportedLanguage>(() =>
    storedLanguage() ?? browserLanguage(),
  )

  const setLang = useCallback((next: SupportedLanguage) => {
    setLangState(next)
    try {
      localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Stockage indisponible : la langue reste en mémoire.
    }
  }, [])

  useEffect(() => {
    document.documentElement.lang = lang
  }, [lang])

  const t = useCallback(
    (key: I18nKey) => dictionaries[lang][key] ?? dictionaries[DEFAULT_LANGUAGE][key] ?? key,
    [lang],
  )

  const value = useMemo(() => ({ lang, setLang, t }), [lang, setLang, t])
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}

export function useI18n(): I18nContextValue {
  return useContext(I18nContext)
}

/** Langues proposées par le sélecteur (extensible : ajouter ici + dictionnaire). */
export function supportedLanguages(): SupportedLanguage[] {
  return SUPPORTED_LANGUAGES
}
