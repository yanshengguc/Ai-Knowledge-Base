import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发环境代理 /api -> 后端(避免跨域);目标地址来自 .env.development,部署/换端口只改 env 不改代码
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      rollupOptions: {
        output: {
          // 大依赖分包:element-plus / vue 全家桶 / markdown 渲染库各自独立 chunk,
          // 业务代码改动不再让全量 vendor 缓存失效(主包原 1.27MB 超 500kB 警告)
          manualChunks: {
            'element-plus': ['element-plus', '@element-plus/icons-vue'],
            'vue-vendor': ['vue', 'vue-router', 'pinia', 'vue-i18n'],
            'markdown': ['marked', 'dompurify'],
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
