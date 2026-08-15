import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import { imagetools } from 'vite-imagetools'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  resolve: {
    tsconfigPaths: true,
    alias: {
      '@css': fileURLToPath(new URL('./src/shared/css', import.meta.url)),
    },
  },
  plugins: [
    react(),
    imagetools({
      exclude: ['**/assets/random/*'],
      defaultDirectives: (url) => {
        if (url.searchParams.has('defaultopt')) {
          return new URLSearchParams('w=300;600&format=avif;webp&as=picture')
        }
        return new URLSearchParams()
      }
    }),
  ],
})
