import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tailwindcss(),
    react(),
  ],
  resolve: {
    alias: {
      // Alias '@' → src/ pour des imports courts : @/components/ui
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
