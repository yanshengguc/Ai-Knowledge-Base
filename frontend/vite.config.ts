import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import AutoImport from 'unplugin-auto-import/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// 开发环境代理 /api -> 后端(避免跨域);目标地址来自 .env.development,部署/换端口只改 env 不改代码
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [
      vue(),
      // Element Plus 组件/API 按需自动引入,类型声明输出到 src 下
      AutoImport({
        resolvers: [ElementPlusResolver()],
        dts: 'src/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      rollupOptions: {
        output: {
          // 大依赖分包:业务代码改动不再让 vendor 缓存失效。
          // 用函数式按模块 id 归类;不把包名写进数组当 chunk 入口(数组形式会解析到 barrel,使 tree-shaking 失效)。
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            // element-plus 组件交给 Rollup 按共享度自动拆分:若强制归入单一 chunk,
            // 被归类模块的导出会被视为 chunk 入口而不再 tree-shake,导致未用组件(如 date-picker)被打包。
            if (id.includes('@element-plus/icons-vue')) return 'element-plus'
            if (/[\\/]node_modules[\\/](vue|vue-router|pinia|vue-i18n)[\\/]/.test(id)) return 'vue-vendor'
            if (id.includes('marked') || id.includes('dompurify')) return 'markdown'
          },
        },
      },
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_DEV_PROXY_TARGET || 'http://localhost:56382',
          changeOrigin: true,
        },
      },
    },
  }
})
