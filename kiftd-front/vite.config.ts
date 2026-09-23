import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

function normalizeApiPrefix(raw: string | undefined): string {
  let p = (raw ?? '/api').trim()
  if (!p || p === '/') p = '/api'
  if (!p.startsWith('/')) p = `/${p}`
  while (p.length > 1 && p.endsWith('/')) p = p.slice(0, -1)
  return p
}

function normalizeBase(raw: string | undefined): string {
  let b = (raw ?? '/kiftd/').trim() || '/kiftd/'
  if (!b.startsWith('/')) b = `/${b}`
  if (!b.endsWith('/')) b = `${b}/`
  return b
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiPrefix = normalizeApiPrefix(env.VITE_API_PREFIX)
  const base = normalizeBase(env.VITE_BASE)

  return {
    base,
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      outDir: 'dist/kiftd',
      emptyOutDir: true,
    },
    server: {
      port: 5173,
      proxy: {
        [apiPrefix]: {
          target: 'http://localhost:80',
          changeOrigin: true,
        },
        '/webdav': {
          target: 'http://localhost:80',
          changeOrigin: true,
        },
      },
    },
    optimizeDeps: {
      include: ['epubjs', 'jszip', 'pdfjs-dist', '@lingo-reader/mobi-parser'],
    },
  }
})
