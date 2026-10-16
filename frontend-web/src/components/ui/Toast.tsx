import { useCallback, useRef, useState, type ReactNode } from 'react'
import { cn } from '../../utils/cn'
import { ToastContext, type ToastType } from './toast-context'

interface ToastItem {
  id: number
  message: string
  type: ToastType
}

const toastTypeClasses: Record<ToastType, string> = {
  success: 'border-success text-text-primary',
  error: 'border-danger text-text-primary',
  info: 'border-accent-500 text-text-primary',
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const idRef = useRef(0)

  const show = useCallback((message: string, type: ToastType = 'info') => {
    const id = ++idRef.current
    setToasts((prev) => [...prev, { id, message, type }])
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id))
    }, 3500)
  }, [])

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {/* Conteneur des toasts */}
      <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={cn(
              'pointer-events-auto rounded-lg border-l-4 bg-bg-elevated px-4 py-3 shadow-lg',
              toastTypeClasses[toast.type],
            )}
          >
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}