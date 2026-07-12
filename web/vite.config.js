import path from 'node:path';
import { copyFileSync, mkdirSync } from 'node:fs';
import { defineConfig } from 'vite';

const webSourceDir = path.resolve(__dirname, 'src');
const webOutputDir = path.resolve(__dirname, '../src/main/resources/web');
const serverTemplateDir = path.resolve(webSourceDir, 'templates');

const copyServerTemplates = () => ({
  name: 'copy-server-templates',
  closeBundle() {
    mkdirSync(webOutputDir, { recursive: true });
    for (const fileName of ['short-url.html', 'image-view.html', 'image-password.html', 'share-expired.html']) {
      copyFileSync(path.resolve(serverTemplateDir, fileName), path.resolve(webOutputDir, fileName));
    }
  }
});

export default defineConfig({
  plugins: [copyServerTemplates()],
  base: '/web/',
  publicDir: false,
  resolve: {
    alias: [
      {
        find: /^\/web\//,
        replacement: `${webSourceDir}/`
      }
    ]
  },
  server: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': 'http://localhost:60000',
      '/auth': 'http://localhost:60000',
      '/logout': 'http://localhost:60000'
    }
  },
  build: {
    outDir: webOutputDir,
    // Do not clear Java-side templates/assets in src/main/resources/web.
    emptyOutDir: false,
    // Disabled to avoid watch-mode directory conflicts on Windows (ENOTEMPTY).
    copyPublicDir: false,
    modulePreload: {
      polyfill: false
    },
    rollupOptions: {
      input: {
        app: path.resolve(webSourceDir, 'main.js'),
        shortUrl: path.resolve(webSourceDir, 'short-url.js')
      },
      output: {
        entryFileNames: (chunkInfo) => {
          if (chunkInfo.name === 'app') {
            return 'app.js';
          }
          if (chunkInfo.name === 'shortUrl') {
            return 'short-url.js';
          }
          return 'chunks/[name]-[hash].js';
        },
        chunkFileNames: 'chunks/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          if (assetInfo.name && assetInfo.name.endsWith('.css')) {
            return 'app.css';
          }
          return 'assets/[name]-[hash][extname]';
        }
      }
    }
  }
});
