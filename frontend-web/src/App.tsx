import { RouterProvider } from 'react-router/dom'
import router from './routes'
import { AuthProvider } from './features/auth/AuthContext'
import { ThemeProvider } from './features/theme/ThemeContext'
import { ToastProvider } from './components/ui'
import { I18nProvider } from './i18n'
import { DataSaverProvider } from './features/region/DataSaverContext'

function App() {
  return (
    <ThemeProvider>
      <I18nProvider>
        <DataSaverProvider>
          <ToastProvider>
            <AuthProvider>
              <RouterProvider router={router} />
            </AuthProvider>
          </ToastProvider>
        </DataSaverProvider>
      </I18nProvider>
    </ThemeProvider>
  )
}

export default App
