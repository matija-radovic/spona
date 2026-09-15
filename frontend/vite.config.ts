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
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
      '^/storage/': {
        target: 'http://localhost:9000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/storage/, ''),
      },
    },
  },
})
