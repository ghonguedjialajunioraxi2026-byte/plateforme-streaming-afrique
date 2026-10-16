/// Configuration de test SÉPARÉE de vite.config.ts (étape 08) :
/// `defineConfig` de 'vitest/config' embarque ses propres types Vite
/// (rollup) incompatibles avec ceux du projet (rolldown) sous `tsc -b`.
/// Ce fichier n'est PAS inclus dans tsconfig.node.json → ignoré par le build.
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
