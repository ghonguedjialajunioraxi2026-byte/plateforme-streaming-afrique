import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

/// Mode économie de données (étape 19) — qualité réduite, préchargement
/// limité, animations réduites. Persisté localement, exposé au player
/// et aux listes d'images.

const STORAGE_KEY = 'flick-data-saver'

function stored(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

interface DataSaverValue {
  enabled: boolean
  toggle: () => void
}

const DataSaverContext = createContext<DataSaverValue>({ enabled: false, toggle: () => undefined })

export function DataSaverProvider({ children }: { children: ReactNode }) {
  const [enabled, setEnabled] = useState<boolean>(stored)

  const toggle = useCallback(() => {
    setEnabled((current) => {
      const next = !current
      try {
        localStorage.setItem(STORAGE_KEY, next ? '1' : '0')
      } catch {
        // Stockage indisponible : reste en mémoire.
      }
      return next
    })
  }, [])

  useEffect(() => {
    document.documentElement.dataset.dataSaver = enabled ? 'on' : 'off'
  }, [enabled])

  const value = useMemo(() => ({ enabled, toggle }), [enabled, toggle])
  return <DataSaverContext.Provider value={value}>{children}</DataSaverContext.Provider>
}

export function useDataSaver(): DataSaverValue {
  return useContext(DataSaverContext)
}
