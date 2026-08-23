/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 基础路径(.env.development / .env.production) */
  readonly VITE_API_BASE?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
