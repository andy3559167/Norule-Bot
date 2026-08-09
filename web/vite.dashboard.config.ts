import path from 'node:path'
import { copyFileSync, mkdirSync } from 'node:fs'
import { defineConfig } from 'vite'

const webRoot = import.meta.dirname
const sourceDir = path.resolve(webRoot, 'src')
const outputDir = process.env.NORULE_WEB_OUTPUT_DIR
  ? path.resolve(process.env.NORULE_WEB_OUTPUT_DIR)
  : path.resolve(webRoot, '../src/main/resources/web')
const templateDir = path.resolve(sourceDir, 'templates')

function copyJavaTemplates() {
  return {
    name: 'copy-java-templates',
    closeBundle() {
      mkdirSync(outputDir, { recursive: true })
      for (const fileName of ['image-view.html', 'image-password.html', 'share-expired.html']) {
        copyFileSync(path.resolve(templateDir, fileName), path.resolve(outputDir, fileName))
      }
    },
  }
}

export default defineConfig({
  plugins: [copyJavaTemplates()],
  base: '/web/',
  publicDir: false,
  resolve: {
    alias: [{ find: /^\/web\//, replacement: `${sourceDir}/` }],
  },
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://127.0.0.1:60000',
      '/auth': 'http://127.0.0.1:60000',
      '/logout': 'http://127.0.0.1:60000',
    },
  },
  build: {
    outDir: outputDir,
    emptyOutDir: false,
    copyPublicDir: false,
    modulePreload: { polyfill: false },
    rollupOptions: {
      input: { app: path.resolve(sourceDir, 'main.js') },
      output: {
        entryFileNames: 'app.js',
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: (assetInfo) =>
          assetInfo.name?.endsWith('.css') ? 'app.css' : 'assets/[name]-[hash][extname]',
      },
    },
  },
})
