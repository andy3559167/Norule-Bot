# NoRule Web Workspace

`web/` 是 NoRule 的共用前端 workspace。NoRule URL 首頁使用 Nuxt 4、Vue 與 TypeScript；既有 Dashboard 在完成後續遷移前，暫時由獨立的 Vite 設定建置。

正式環境不需要 Node.js 或 Nuxt server。瀏覽器資產會在 Maven package 階段寫入 Java classpath，啟動方式仍是：

```bash
java -Dfile.encoding=UTF-8 -jar target/discord-music-bot-1.6.jar
```

## 目錄

```text
web/
├─ app/                         # Nuxt 4 / NoRule URL
│  ├─ assets/css/               # --nr-* design tokens 與 base styles
│  ├─ components/
│  │  ├─ ui/                    # Dashboard 可重用的基礎元件
│  │  ├─ layout/
│  │  ├─ short-url/
│  │  └─ media/
│  ├─ composables/              # API、upload 與 clipboard 狀態
│  ├─ pages/index.vue
│  ├─ types/
│  └─ utils/
├─ scripts/sync-static-output.mjs
├─ src/                         # 暫留 Dashboard Vanilla 原始碼與 Java 特殊頁模板
├─ nuxt.config.ts
└─ vite.dashboard.config.ts
```

Java 仍負責 `/api/**`、OAuth、Session、Redirect、Rate Limit、資料庫、媒體內容與靜態檔案服務。Nuxt 不提供 server API。

## 開發

需求：Node.js 22.19+ 或 24.11+。

```bash
cd web
npm ci
npm run dev
```

Nuxt dev server 預設把 `/api` 代理至 `http://127.0.0.1:60001`。可覆寫：

```powershell
$env:NUXT_DEV_API_TARGET='http://127.0.0.1:60001'
npm run dev
```

Dashboard 的 Vite dev server：

```bash
npm run dev:dashboard
```

## 檢查與建置

```bash
npm run typecheck
npm test
npm run build
```

`npm run build` 依序執行：

1. Dashboard Vite build。
2. `nuxt generate`，產生 `.output/public`。
3. 同步 Nuxt `index.html` 與 `/web/short-url/_nuxt/` hashed assets。

未設定 `NORULE_WEB_OUTPUT_DIR` 時，產物同步至 `src/main/resources/web`。Maven 會設定此變數為 `target/classes/web`，因此普通 `mvn package` 不會把 Node modules、`.vue`、`.ts` 或 `.output/server` 放入 JAR。

## Maven production build

在專案根目錄執行：

```bash
mvn package
```

Maven 的 `prepare-package` 會自動執行 `npm ci` 與完整前端 build。Production 只執行 Java JAR，不需要 `npm run start`、Nuxt SSR server 或獨立前端 container。

圖片／影片 viewer、密碼驗證、分享過期與 Java 404 仍由 Java 模板渲染；它們的樣式由 Dashboard bundle 中的 `media-share-pages.css` 提供，避免 Nuxt global CSS 影響既有頁面。
